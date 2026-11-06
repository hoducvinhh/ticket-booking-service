package space.ducvinh.ticket_booking_service.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogEntity;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.payment.entity.PaymentEntity;
import space.ducvinh.ticket_booking_service.payment.entity.WebhookEventEntity;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;

public class AdminDtos {

    @Data
    public static class UpdateUserStatusRequest {
        @NotNull(message = "User status is required")
        private UserStatus status;
    }

    @Data
    public static class AssignRoleRequest {
        @NotBlank(message = "Role name is required")
        private String roleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminEventSummary {
        private UUID id;
        private UUID organizerId;
        private UUID venueId;
        private String name;
        private String slug;
        private String category;
        private String status;
        private boolean waitingRoomEnabled;
        private boolean deleted;
        private Instant startTime;
        private Instant salesStartTime;
        private Instant salesEndTime;
        private Instant createdAt;

        public static AdminEventSummary from(EventEntity e) {
            return AdminEventSummary.builder()
                    .id(e.getId())
                    .organizerId(e.getOrganizerId())
                    .venueId(e.getVenueId())
                    .name(e.getName())
                    .slug(e.getSlug())
                    .category(e.getCategory())
                    .status(e.getStatus().name())
                    .waitingRoomEnabled(e.isWaitingRoomEnabled())
                    .deleted(e.isDeleted())
                    .startTime(e.getStartTime())
                    .salesStartTime(e.getSalesStartTime())
                    .salesEndTime(e.getSalesEndTime())
                    .createdAt(e.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOrderSummary {
        private UUID id;
        private UUID userId;
        private UUID reservationId;
        private UUID eventId;
        private String orderCode;
        private long totalAmount;
        private String currency;
        private String status;
        private Instant createdAt;

        public static AdminOrderSummary from(OrderEntity o) {
            return AdminOrderSummary.builder()
                    .id(o.getId())
                    .userId(o.getUserId())
                    .reservationId(o.getReservationId())
                    .eventId(o.getEventId())
                    .orderCode(o.getOrderCode())
                    .totalAmount(o.getTotalAmount())
                    .currency(o.getCurrency())
                    .status(o.getStatus().name())
                    .createdAt(o.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPaymentSummary {
        private UUID id;
        private UUID orderId;
        private String provider;
        private String providerPaymentId;
        private long amount;
        private String status;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;

        public static AdminPaymentSummary from(PaymentEntity p) {
            return AdminPaymentSummary.builder()
                    .id(p.getId())
                    .orderId(p.getOrderId())
                    .provider(p.getProvider())
                    .providerPaymentId(p.getProviderPaymentId())
                    .amount(p.getAmount())
                    .status(p.getStatus().name())
                    .failureReason(p.getFailureReason())
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminWebhookSummary {
        private UUID id;
        private String provider;
        private String providerEventId;
        private String status;
        private String errorMessage;
        private Instant receivedAt;
        private Instant processedAt;

        public static AdminWebhookSummary from(WebhookEventEntity w) {
            return AdminWebhookSummary.builder()
                    .id(w.getId())
                    .provider(w.getProvider())
                    .providerEventId(w.getProviderEventId())
                    .status(w.getStatus())
                    .errorMessage(w.getErrorMessage())
                    .receivedAt(w.getReceivedAt())
                    .processedAt(w.getProcessedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminAuditLogSummary {
        private UUID id;
        private UUID actorId;
        private String action;
        private String resourceType;
        private UUID resourceId;
        private String metadata;
        private Instant createdAt;

        public static AdminAuditLogSummary from(AuditLogEntity a) {
            return AdminAuditLogSummary.builder()
                    .id(a.getId())
                    .actorId(a.getActorId())
                    .action(a.getAction())
                    .resourceType(a.getResourceType())
                    .resourceId(a.getResourceId())
                    .metadata(a.getMetadata())
                    .createdAt(a.getCreatedAt())
                    .build();
        }
    }
}
