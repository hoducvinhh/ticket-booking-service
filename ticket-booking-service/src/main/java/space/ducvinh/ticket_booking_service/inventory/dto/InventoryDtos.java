package space.ducvinh.ticket_booking_service.inventory.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class InventoryDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatMapResponse {
        private UUID eventId;
        private List<SectionSeatMap> sections;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionSeatMap {
        private UUID sectionId;
        private String sectionName;
        private long price;
        private String type;
        private List<SeatNode> seats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatNode {
        private UUID id;
        private String seatCode;
        private String rowLabel;
        private int seatNumber;
        private String status;
        private long price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityResponse {
        private UUID eventId;
        private long totalSeats;
        private long availableSeats;
        private long heldSeats;
        private long soldSeats;
        private long blockedSeats;
    }
}
