package space.ducvinh.ticket_booking_service.waitingroom.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class WaitingRoomDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitingRoomStatusResponse {
        private UUID eventId;
        private UUID userId;
        private String status;
        private Long queuePosition;
        private long totalWaiting;
        private long estimatedWaitSeconds;
        private String admissionToken;
        private Instant tokenExpiresAt;
    }
}
