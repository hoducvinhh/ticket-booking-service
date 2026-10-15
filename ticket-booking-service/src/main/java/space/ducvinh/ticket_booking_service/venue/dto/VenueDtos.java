package space.ducvinh.ticket_booking_service.venue.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;

public class VenueDtos {

    @Data
    public static class CreateVenueRequest {
        @NotBlank
        private String name;
        private String address;
        private String description;
        @Min(1)
        private int capacity;
    }

    @Data
    public static class CreateSectionRequest {
        @NotNull
        private UUID eventId;
        @NotBlank
        private String name;
        @Min(0)
        private long price;
        @Min(1)
        private int capacity;
        private String type = "RESERVED";
    }

    @Data
    public static class CreateSeatsRequest {
        @NotEmpty
        private List<SeatItem> seats;
        private boolean autoGenerate;
        private String rowLabel;
        private Integer startNumber;
        private Integer count;
    }

    @Data
    public static class SeatItem {
        @NotBlank
        private String seatCode;
        @NotBlank
        private String rowLabel;
        @Min(1)
        private int seatNumber;
    }

    @Data
    public static class GenerateSeatsRequest {
        @NotEmpty
        private List<String> rows;
        @Min(1)
        private int seatsPerRow;
    }

    @Data
    public static class UpdateSeatStatusRequest {
        @NotNull
        private SeatStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueResponse {
        private UUID id;
        private UUID organizerId;
        private String name;
        private String address;
        private String description;
        private int capacity;
        private Instant createdAt;

        public static VenueResponse from(VenueEntity v) {
            return VenueResponse.builder()
                    .id(v.getId())
                    .organizerId(v.getOrganizerId())
                    .name(v.getName())
                    .address(v.getAddress())
                    .description(v.getDescription())
                    .capacity(v.getCapacity())
                    .createdAt(v.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionResponse {
        private UUID id;
        private UUID eventId;
        private String name;
        private long price;
        private int capacity;
        private String type;

        public static SectionResponse from(TicketSectionEntity s) {
            return SectionResponse.builder()
                    .id(s.getId())
                    .eventId(s.getEventId())
                    .name(s.getName())
                    .price(s.getPrice())
                    .capacity(s.getCapacity())
                    .type(s.getType())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatResponse {
        private UUID id;
        private UUID sectionId;
        private String seatCode;
        private String rowLabel;
        private int seatNumber;
        private String status;

        public static SeatResponse from(SeatEntity s) {
            return SeatResponse.builder()
                    .id(s.getId())
                    .sectionId(s.getSectionId())
                    .seatCode(s.getSeatCode())
                    .rowLabel(s.getRowLabel())
                    .seatNumber(s.getSeatNumber())
                    .status(s.getStatus().name())
                    .build();
        }
    }
}
