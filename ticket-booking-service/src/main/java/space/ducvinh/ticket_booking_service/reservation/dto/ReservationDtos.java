package space.ducvinh.ticket_booking_service.reservation.dto;

import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ReservationDtos {

    @Data
    public static class CreateReservationRequest {
        private List<UUID> seatIds;

        private UUID ticketTypeId;

        @Min(1)
        private Integer quantity;

        private String admissionToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationItemDto {
        private UUID seatId;
        private UUID ticketTypeId;
        private String seatCode;
        private String sectionName;
        private int quantity;
        private long unitPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationResponse {
        private UUID reservationId;
        private UUID eventId;
        private UUID userId;
        private String status;
        private Instant expiresAt;
        private long totalAmount;
        private Instant createdAt;
        private List<ReservationItemDto> items;
    }
}
