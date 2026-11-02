package space.ducvinh.ticket_booking_service.payment.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PaymentDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentResponse {
        private UUID id;
        private UUID orderId;
        private String provider;
        private String providerPaymentId;
        private long amount;
        private String status;
        private String paymentUrl;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayOsWebhookRequest {
        private String eventId;
        private String paymentId;
        private String orderCode;
        private long amount;
        private String status;
        private String signature;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VnPayWebhookRequest {
        private String vnpTxnRef;
        private String vnpTransactionNo;
        private long vnpAmount;
        private String vnpResponseCode;
        private String vnpSecureHash;
    }

    @Data
    public static class SandboxCompleteRequest {
        private boolean success = true;
        private String eventId;
    }
}
