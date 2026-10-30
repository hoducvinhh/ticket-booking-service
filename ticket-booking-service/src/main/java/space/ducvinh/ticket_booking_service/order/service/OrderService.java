package space.ducvinh.ticket_booking_service.order.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.order.dto.OrderDtos;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderItemEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderItemRepository;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationItemEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationItemRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final InventoryService inventoryService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final OutboxService outboxService;

    @Transactional
    public OrderDtos.OrderResponse createOrder(UUID userId, OrderDtos.CreateOrderRequest req) {
        ReservationEntity reservation = reservationRepository.findByIdForUpdate(req.getReservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        if (!reservation.getUserId().equals(userId)) {
            SecurityUtils.requireOwnerOrAdmin(reservation.getUserId());
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new BusinessRuleViolationException("RESERVATION_NOT_ACTIVE",
                    "Cannot create order for reservation with status " + reservation.getStatus());
        }
        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleViolationException("RESERVATION_EXPIRED", "Reservation has expired");
        }

        var existingOrderOpt = orderRepository.findByReservationId(reservation.getId());
        if (existingOrderOpt.isPresent()) {
            OrderEntity existing = existingOrderOpt.get();
            if (existing.getStatus() == OrderStatus.PAID) {
                throw new ConflictException("Reservation has already been paid");
            }
            return toResponse(existing, reservation);
        }

        List<ReservationItemEntity> resItems = reservationItemRepository.findByReservationId(reservation.getId());
        if (resItems.isEmpty()) {
            throw new BusinessRuleViolationException("Reservation has no items");
        }

        long totalAmount = 0L;
        for (ReservationItemEntity ri : resItems) {
            totalAmount += ri.getUnitPrice() * ri.getQuantity();
        }

        String orderCode = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(userId)
                .reservationId(reservation.getId())
                .eventId(reservation.getEventId())
                .orderCode(orderCode)
                .totalAmount(totalAmount)
                .currency("VND")
                .status(OrderStatus.PENDING_PAYMENT)
                .build());

        List<OrderItemEntity> orderItems = new ArrayList<>();
        for (ReservationItemEntity ri : resItems) {
            orderItems.add(OrderItemEntity.builder()
                    .orderId(order.getId())
                    .seatId(ri.getSeatId())
                    .ticketTypeId(ri.getTicketTypeId())
                    .unitPrice(ri.getUnitPrice())
                    .quantity(ri.getQuantity())
                    .subtotal(ri.getUnitPrice() * ri.getQuantity())
                    .build());
        }
        orderItemRepository.saveAll(orderItems);

        outboxService.enqueue("ORDER", order.getId(), "ORDER_CREATED", Map.of(
                "orderId", order.getId().toString(),
                "orderCode", order.getOrderCode(),
                "userId", userId.toString(),
                "totalAmount", totalAmount
        ));

        return toResponse(order, reservation);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse getOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        SecurityUtils.requireOwnerOrAdmin(order.getUserId());
        ReservationEntity reservation = reservationRepository.findById(order.getReservationId()).orElse(null);
        return toResponse(order, reservation);
    }

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderResponse> listMyOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(o -> toResponse(o, reservationRepository.findById(o.getReservationId()).orElse(null)));
    }

    @Transactional
    public OrderDtos.OrderResponse cancelOrder(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        SecurityUtils.requireOwnerOrAdmin(order.getUserId());

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("Only PENDING_PAYMENT orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        ReservationEntity reservation = reservationRepository.findByIdForUpdate(order.getReservationId()).orElse(null);
        if (reservation != null && reservation.getStatus() == ReservationStatus.ACTIVE) {
            List<UUID> releasedSeats = seatClaimRepository.findByReservationId(reservation.getId()).stream()
                    .map(SeatClaimEntity::getSeatId)
                    .toList();
            seatClaimRepository.deleteByReservationId(reservation.getId());
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);

            UUID eventId = reservation.getEventId();
            UUID resId = reservation.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    inventoryService.evictSeatMapCache(eventId);
                    if (!releasedSeats.isEmpty()) {
                        realtimeBroadcastService.broadcastSeatEvent(eventId, "SEAT_RELEASED", releasedSeats, resId);
                    }
                }
            });
        }

        return toResponse(order, reservation);
    }

    private OrderDtos.OrderResponse toResponse(OrderEntity order, ReservationEntity reservation) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        List<UUID> seatIds = items.stream().map(OrderItemEntity::getSeatId)
                .filter(java.util.Objects::nonNull).toList();
        Map<UUID, SeatEntity> seats = seatRepository.findAllById(seatIds).stream()
                .collect(Collectors.toMap(SeatEntity::getId, s -> s));
        EventEntity event = eventRepository.findById(order.getEventId()).orElse(null);

        List<OrderDtos.OrderItemDto> itemDtos = items.stream().map(i -> {
            SeatEntity s = i.getSeatId() != null ? seats.get(i.getSeatId()) : null;
            return OrderDtos.OrderItemDto.builder()
                    .seatId(i.getSeatId())
                    .seatCode(s != null ? s.getSeatCode() : null)
                    .unitPrice(i.getUnitPrice())
                    .quantity(i.getQuantity())
                    .subtotal(i.getSubtotal())
                    .build();
        }).toList();

        return OrderDtos.OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .reservationId(order.getReservationId())
                .eventId(order.getEventId())
                .eventName(event != null ? event.getName() : null)
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .reservationExpiresAt(reservation != null ? reservation.getExpiresAt() : null)
                .items(itemDtos)
                .build();
    }
}
