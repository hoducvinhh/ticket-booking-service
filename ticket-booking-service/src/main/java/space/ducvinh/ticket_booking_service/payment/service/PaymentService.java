package space.ducvinh.ticket_booking_service.payment.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogService;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.exception.UnauthorizedException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.order.dto.OrderDtos;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderItemEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderItemRepository;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.payment.dto.PaymentDtos;
import space.ducvinh.ticket_booking_service.payment.entity.IdempotencyRecordEntity;
import space.ducvinh.ticket_booking_service.payment.entity.PaymentEntity;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;
import space.ducvinh.ticket_booking_service.payment.repository.IdempotencyRecordRepository;
import space.ducvinh.ticket_booking_service.payment.repository.PaymentRepository;
import space.ducvinh.ticket_booking_service.payment.repository.WebhookEventRepository;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketTypeRepository;
import space.ducvinh.ticket_booking_service.ticket.service.TicketIssuanceService;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketIssuanceService ticketIssuanceService;
    private final PaymentGatewayAdapter gatewayAdapter;
    private final InventoryService inventoryService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final OutboxService outboxService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderDtos.CheckoutResponse checkout(
            UUID userId,
            UUID orderId,
            String idempotencyKey,
            OrderDtos.CheckoutRequest req) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        String endpoint = "/api/v1/orders/" + orderId + "/checkout";
        String requestHash = JwtTokenProvider.sha256Hex(orderId + "|" + req.getProvider().name());

        var existingIdem = idempotencyRecordRepository
                .findByUserIdAndEndpointAndIdempotencyKey(userId, endpoint, idempotencyKey);
        if (existingIdem.isPresent()) {
            IdempotencyRecordEntity record = existingIdem.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key already used with a different request payload");
            }
            return objectMapper.readValue(record.getResponseBody(), OrderDtos.CheckoutResponse.class);
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is locked or disabled");
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUserId().equals(userId)) {
            SecurityUtils.requireOwnerOrAdmin(order.getUserId());
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("ORDER_NOT_PAYABLE",
                    "Order is not in PENDING_PAYMENT state: " + order.getStatus());
        }

        ReservationEntity reservation = reservationRepository.findById(order.getReservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ACTIVE || !reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleViolationException("RESERVATION_EXPIRED",
                    "Reservation has expired and cannot be checked out");
        }

        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(req.getProvider().name())
                .idempotencyKey(idempotencyKey)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PROCESSING)
                .build());

        PaymentGatewayAdapter.GatewayPaymentInitResult initResult =
                gatewayAdapter.initiatePayment(req.getProvider(), payment.getId(), order);
        payment.setProviderPaymentId(initResult.providerPaymentId());
        payment.setPaymentUrl(initResult.paymentUrl());
        payment = paymentRepository.save(payment);

        OrderDtos.CheckoutResponse response = OrderDtos.CheckoutResponse.builder()
                .paymentId(payment.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .provider(payment.getProvider())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .paymentUrl(payment.getPaymentUrl())
                .build();

        idempotencyRecordRepository.save(IdempotencyRecordEntity.builder()
                .userId(userId)
                .endpoint(endpoint)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .responseStatus(200)
                .responseBody(objectMapper.writeValueAsString(response))
                .expiresAt(Instant.now().plusSeconds(86400))
                .build());

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse getPayment(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        OrderEntity order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        SecurityUtils.requireOwnerOrAdmin(order.getUserId());

        return PaymentDtos.PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .provider(payment.getProvider())
                .providerPaymentId(payment.getProviderPaymentId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .paymentUrl(payment.getPaymentUrl())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    @Transactional
    public void handlePayOsWebhook(PaymentDtos.PayOsWebhookRequest req) {
        if (!gatewayAdapter.verifyPayOsSignature(req.getOrderCode(), req.getAmount(), req.getStatus(), req.getSignature())) {
            auditLogService.record(null, "WEBHOOK_INVALID_SIGNATURE", "WEBHOOK", null,
                    Map.of("provider", "PAYOS", "orderCode", String.valueOf(req.getOrderCode())));
            throw new UnauthorizedException("Invalid PayOS webhook signature");
        }
        String rawJson = objectMapper.writeValueAsString(req);
        boolean success = "PAID".equalsIgnoreCase(req.getStatus()) || "SUCCESS".equalsIgnoreCase(req.getStatus());
        processVerifiedWebhook(
                PaymentProvider.PAYOS.name(),
                req.getEventId(),
                req.getPaymentId(),
                req.getOrderCode(),
                req.getAmount(),
                success,
                rawJson);
    }

    @Transactional
    public void handleVnPayWebhook(PaymentDtos.VnPayWebhookRequest req) {
        if (!gatewayAdapter.verifyVnPaySignature(
                req.getVnpTxnRef(), req.getVnpAmount(), req.getVnpResponseCode(), req.getVnpSecureHash())) {
            auditLogService.record(null, "WEBHOOK_INVALID_SIGNATURE", "WEBHOOK", null,
                    Map.of("provider", "VNPAY", "orderCode", String.valueOf(req.getVnpTxnRef())));
            throw new UnauthorizedException("Invalid VNPay webhook signature");
        }
        String rawJson = objectMapper.writeValueAsString(req);
        boolean success = "00".equals(req.getVnpResponseCode());
        long amountVnd = req.getVnpAmount() / 100L;
        processVerifiedWebhook(
                PaymentProvider.VNPAY.name(),
                req.getVnpTransactionNo(),
                req.getVnpTransactionNo(),
                req.getVnpTxnRef(),
                amountVnd,
                success,
                rawJson);
    }

    @Transactional
    public PaymentDtos.PaymentResponse completeSandboxPayment(UUID paymentId, PaymentDtos.SandboxCompleteRequest req) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        OrderEntity order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        String eventId = (req != null && req.getEventId() != null && !req.getEventId().isBlank())
                ? req.getEventId()
                : "SBX-EVT-" + paymentId;
        boolean success = req == null || req.isSuccess();
        String statusStr = success ? "PAID" : "FAILED";
        String sig = gatewayAdapter.signPayOsWebhook(order.getOrderCode(), order.getTotalAmount(), statusStr);

        handlePayOsWebhook(new PaymentDtos.PayOsWebhookRequest(
                eventId,
                payment.getProviderPaymentId() != null ? payment.getProviderPaymentId() : paymentId.toString(),
                order.getOrderCode(),
                order.getTotalAmount(),
                statusStr,
                sig));

        return getPaymentByIdInternal(paymentId);
    }

    private void processVerifiedWebhook(
            String provider,
            String providerEventId,
            String providerPaymentId,
            String orderCode,
            long amount,
            boolean success,
            String rawJson) {

        String payloadHash = JwtTokenProvider.sha256Hex(rawJson);
        int inserted = webhookEventRepository.insertIfAbsent(
                UUID.randomUUID(), provider, providerEventId, payloadHash, rawJson, Instant.now());

        if (inserted == 0) {
            log.info("Duplicate webhook ignored idempotently: provider={}, eventId={}", provider, providerEventId);
            return;
        }

        var webhookEvent = webhookEventRepository.findByProviderAndProviderEventId(provider, providerEventId)
                .orElseThrow();

        try {
            OrderEntity order = orderRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found for orderCode=" + orderCode));
            order = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();

            if (order.getTotalAmount() != amount) {
                webhookEvent.setStatus("FAILED");
                webhookEvent.setErrorMessage("Amount mismatch: expected " + order.getTotalAmount() + ", got " + amount);
                webhookEvent.setProcessedAt(Instant.now());
                webhookEventRepository.save(webhookEvent);
                throw new BusinessRuleViolationException("AMOUNT_MISMATCH", "Webhook payment amount does not match order");
            }

            List<PaymentEntity> orderPayments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
            PaymentEntity payment = orderPayments.stream()
                    .filter(p -> providerPaymentId != null && providerPaymentId.equals(p.getProviderPaymentId()))
                    .findFirst()
                    .orElse(orderPayments.isEmpty() ? null : orderPayments.get(0));

            if (payment == null) {
                payment = paymentRepository.save(PaymentEntity.builder()
                        .orderId(order.getId())
                        .provider(provider)
                        .providerPaymentId(providerPaymentId)
                        .idempotencyKey("WEBHOOK-" + providerEventId)
                        .amount(amount)
                        .status(PaymentStatus.PROCESSING)
                        .build());
            } else {
                payment = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
            }

            // Guard against duplicate success confirmation across distinct webhook events
            if (payment.getStatus() == PaymentStatus.SUCCESS || order.getStatus() == OrderStatus.PAID) {
                webhookEvent.setStatus("PROCESSED");
                webhookEvent.setProcessedAt(Instant.now());
                webhookEventRepository.save(webhookEvent);
                return;
            }

            if (!success) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Provider reported failure");
                paymentRepository.save(payment);
                webhookEvent.setStatus("PROCESSED");
                webhookEvent.setProcessedAt(Instant.now());
                webhookEventRepository.save(webhookEvent);
                return;
            }

            ReservationEntity reservation = reservationRepository.findByIdForUpdate(order.getReservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
            Instant now = Instant.now();

            // Section 10.4: Payment Success when Reservation has already expired
            if (reservation.getStatus() != ReservationStatus.ACTIVE || !reservation.getExpiresAt().isAfter(now)) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason("Payment received after reservation expired; refund required");
                paymentRepository.save(payment);

                order.setStatus(OrderStatus.REFUND_PENDING);
                orderRepository.save(order);

                outboxService.enqueue("ORDER", order.getId(), "PAYMENT_EXPIRED_REFUND_NEEDED", Map.of(
                        "orderId", order.getId().toString(),
                        "orderCode", order.getOrderCode(),
                        "userId", order.getUserId().toString(),
                        "amount", order.getTotalAmount()
                ));
                auditLogService.record(order.getUserId(), "PAYMENT_AFTER_EXPIRATION_REFUND_PENDING",
                        "ORDER", order.getId(), Map.of("paymentId", payment.getId().toString()));

                webhookEvent.setStatus("PROCESSED");
                webhookEvent.setProcessedAt(Instant.now());
                webhookEventRepository.save(webhookEvent);
                return;
            }

            // Happy path: Confirm payment, order, reservation, mark seats SOLD, issue tickets
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);

            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);

            seatClaimRepository.deleteByReservationId(reservation.getId());

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            List<UUID> soldSeatIds = items.stream()
                    .map(OrderItemEntity::getSeatId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!soldSeatIds.isEmpty()) {
                List<SeatEntity> seats = seatRepository.findAllById(soldSeatIds);
                seats.forEach(s -> s.setStatus(SeatStatus.SOLD));
                seatRepository.saveAll(seats);
            }

            for (OrderItemEntity item : items) {
                if (item.getTicketTypeId() != null) {
                    int ok = ticketTypeRepository.incrementSoldQuantityAtomically(
                            item.getTicketTypeId(), Math.max(1, item.getQuantity()));
                    if (ok == 0) {
                        throw new ConflictException("INSUFFICIENT_TICKETS", "General admission inventory exhausted");
                    }
                }
            }

            String userEmail = userRepository.findById(order.getUserId()).map(UserEntity::getEmail).orElse("");
            ticketIssuanceService.issueTicketsForPaidOrder(order, userEmail);

            outboxService.enqueue("ORDER", order.getId(), "PAYMENT_SUCCEEDED", Map.of(
                    "orderId", order.getId().toString(),
                    "orderCode", order.getOrderCode(),
                    "userId", order.getUserId().toString(),
                    "email", userEmail,
                    "amount", order.getTotalAmount()
            ));

            webhookEvent.setStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);

            UUID eventId = order.getEventId();
            UUID resId = reservation.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    inventoryService.evictSeatMapCache(eventId);
                    if (!soldSeatIds.isEmpty()) {
                        realtimeBroadcastService.broadcastSeatEvent(eventId, "SEAT_SOLD", soldSeatIds, resId);
                    }
                }
            });
        } catch (RuntimeException ex) {
            webhookEvent.setStatus("FAILED");
            webhookEvent.setErrorMessage(ex.getMessage());
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);
            throw ex;
        }
    }

    private PaymentDtos.PaymentResponse getPaymentByIdInternal(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        return PaymentDtos.PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .provider(payment.getProvider())
                .providerPaymentId(payment.getProviderPaymentId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .paymentUrl(payment.getPaymentUrl())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
