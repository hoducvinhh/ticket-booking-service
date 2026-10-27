package space.ducvinh.ticket_booking_service.ticket.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TicketDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketResponse {
        private UUID id;
        private UUID orderId;
        private UUID eventId;
        private String eventName;
        private UUID seatId;
        private String seatCode;
        private String sectionName;
        private String ticketCode;
        private String status;
        private Instant checkedInAt;
        private Instant issuedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketQrResponse {
        private UUID ticketId;
        private String ticketCode;
        private String qrToken;
        private String qrImageBase64;
        private String status;
    }
}
