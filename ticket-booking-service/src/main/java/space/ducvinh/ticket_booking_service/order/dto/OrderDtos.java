package space.ducvinh.ticket_booking_service.order.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;

public class OrderDtos {

    @Data
    public static class CreateOrderRequest {
        @NotNull(message = "reservationId is required")
        private UUID reservationId;
    }

    @Data
    public static class CheckoutRequest {
        @NotNull(message = "provider is required")
        private PaymentProvider provider;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private UUID seatId;
        private String seatCode;
        private long unitPrice;
        private int quantity;
        private long subtotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private UUID id;
        private UUID userId;
        private UUID reservationId;
        private UUID eventId;
        private String eventName;
        private String orderCode;
        private long totalAmount;
        private String currency;
        private String status;
        private Instant createdAt;
        private Instant reservationExpiresAt;
        private List<OrderItemDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutResponse {
        private UUID paymentId;
        private UUID orderId;
        private String orderCode;
        private String provider;
        private long amount;
        private String status;
        private String paymentUrl;
    }
}
