package space.ducvinh.ticket_booking_service.admin.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.admin.dto.AdminDtos;
import space.ducvinh.ticket_booking_service.auth.repository.RefreshTokenRepository;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogRepository;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogService;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderItemEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderItemRepository;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.payment.entity.PaymentEntity;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;
import space.ducvinh.ticket_booking_service.payment.repository.PaymentRepository;
import space.ducvinh.ticket_booking_service.payment.repository.WebhookEventRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.user.dto.UserDtos;
import space.ducvinh.ticket_booking_service.user.entity.RoleEntity;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.RoleRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final InventoryService inventoryService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final OutboxService outboxService;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public Page<UserDtos.UserProfileResponse> listUsers(String role, String search, Pageable pageable) {
        Specification<UserEntity> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (role != null && !role.isBlank()) {
                var rolesJoin = root.join("roles", JoinType.LEFT);
                predicates.add(cb.equal(rolesJoin.get("name"), role.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(UserDtos.UserProfileResponse::from);
    }

    @Transactional
    public UserDtos.UserProfileResponse updateUserStatus(UUID actorId, UUID targetUserId, UserStatus status) {
        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(status);
        user = userRepository.save(user);
        if (status != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllByUserId(targetUserId, Instant.now());
        }
        auditLogService.record(actorId, "ADMIN_UPDATE_USER_STATUS",
                "USER", targetUserId, Map.of("status", status.name()));
        return UserDtos.UserProfileResponse.from(user);
    }

    @Transactional
    public UserDtos.UserProfileResponse assignRole(UUID actorId, UUID targetUserId, String roleName) {
        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RoleEntity role = roleRepository.findByName(roleName.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        user.getRoles().add(role);
        user = userRepository.save(user);
        auditLogService.record(actorId, "ADMIN_ASSIGN_ROLE",
                "USER", targetUserId, Map.of("role", role.getName()));
        return UserDtos.UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminEventSummary> listAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable).map(AdminDtos.AdminEventSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminOrderSummary> listAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(AdminDtos.AdminOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminPaymentSummary> listAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(AdminDtos.AdminPaymentSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminWebhookSummary> listWebhooks(Pageable pageable) {
        return webhookEventRepository.findAll(pageable).map(AdminDtos.AdminWebhookSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminAuditLogSummary> listAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(AdminDtos.AdminAuditLogSummary::from);
    }

    @Transactional
    public AdminDtos.AdminOrderSummary processRefund(UUID actorId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.REFUND_PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleViolationException("Order cannot be refunded in status " + order.getStatus());
        }

        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        for (PaymentEntity p : payments) {
            if (p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUND_PENDING) {
                p.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(p);
            }
        }

        List<TicketEntity> tickets = ticketRepository.findByOrderId(orderId);
        for (TicketEntity t : tickets) {
            if (t.getStatus() == TicketStatus.VALID) {
                t.setStatus(TicketStatus.REFUNDED);
                ticketRepository.save(t);
            }
        }

        List<UUID> releasedSeats = orderItemRepository.findByOrderId(orderId).stream()
                .map(OrderItemEntity::getSeatId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!releasedSeats.isEmpty()) {
            List<SeatEntity> seats = seatRepository.findAllById(releasedSeats);
            for (SeatEntity s : seats) {
                if (s.getStatus() == SeatStatus.SOLD) {
                    s.setStatus(SeatStatus.AVAILABLE);
                }
            }
            seatRepository.saveAll(seats);
            inventoryService.evictSeatMapCache(order.getEventId());
            realtimeBroadcastService.broadcastSeatEvent(order.getEventId(), "SEAT_RELEASED", releasedSeats, order.getReservationId());
        }

        String email = userRepository.findById(order.getUserId()).map(UserEntity::getEmail).orElse("");
        outboxService.enqueue("ORDER", order.getId(), "REFUND_SUCCEEDED", Map.of(
                "orderId", order.getId().toString(),
                "orderCode", order.getOrderCode(),
                "userId", order.getUserId().toString(),
                "email", email,
                "amount", order.getTotalAmount()
        ));

        auditLogService.record(actorId, "ADMIN_REFUND_PROCESSED", "ORDER", orderId,
                Map.of("orderCode", order.getOrderCode(), "amount", order.getTotalAmount()));
        return AdminDtos.AdminOrderSummary.from(order);
    }
}
