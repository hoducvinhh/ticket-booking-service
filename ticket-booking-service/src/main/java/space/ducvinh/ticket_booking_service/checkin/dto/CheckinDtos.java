package space.ducvinh.ticket_booking_service.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CheckinDtos {

    @Data
    public static class ScanQrRequest {
        @NotNull(message = "eventId is required")
        private UUID eventId;

        @NotBlank(message = "qrToken is required")
        private String qrToken;

        private String deviceInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckinScanResponse {
        private UUID ticketId;
        private String ticketCode;
        private UUID eventId;
        private UUID seatId;
        private String status;
        private Instant checkedInAt;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckinLogResponse {
        private UUID id;
        private UUID ticketId;
        private UUID staffId;
        private UUID eventId;
        private Instant checkedInAt;
        private String deviceInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckinStatisticsResponse {
        private UUID eventId;
        private long totalIssuedTickets;
        private long checkedInCount;
        private long remainingCount;
    }
}
