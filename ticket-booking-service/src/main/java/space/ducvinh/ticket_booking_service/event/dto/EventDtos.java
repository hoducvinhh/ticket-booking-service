package space.ducvinh.ticket_booking_service.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;

public class EventDtos {

    @Data
    public static class CreateEventRequest {
        @NotNull
        private UUID venueId;
        @NotBlank
        private String name;
        private String slug;
        private String category;
        private String description;
        private String bannerUrl;
        @NotNull
        private Instant startTime;
        @NotNull
        private Instant endTime;
        @NotNull
        private Instant salesStartTime;
        @NotNull
        private Instant salesEndTime;
        private boolean waitingRoomEnabled;
        @Min(1)
        private int maxConcurrentUsers = 300;
        @Min(1)
        private int maxSeatsPerReservation = 6;
    }

    @Data
    public static class UpdateEventRequest {
        private UUID venueId;
        private String name;
        private String category;
        private String description;
        private String bannerUrl;
        private Instant startTime;
        private Instant endTime;
        private Instant salesStartTime;
        private Instant salesEndTime;
        private Boolean waitingRoomEnabled;
        private Integer maxConcurrentUsers;
        private Integer maxSeatsPerReservation;
    }

    @Data
    public static class AssignStaffRequest {
        @NotNull
        private UUID staffId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventResponse {
        private UUID id;
        private UUID organizerId;
        private UUID venueId;
        private String venueName;
        private String venueAddress;
        private String name;
        private String slug;
        private String category;
        private String description;
        private String bannerUrl;
        private Instant startTime;
        private Instant endTime;
        private Instant salesStartTime;
        private Instant salesEndTime;
        private boolean waitingRoomEnabled;
        private int maxConcurrentUsers;
        private int maxSeatsPerReservation;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;
        private List<SectionSummary> sections;

        public static EventResponse from(EventEntity e, String venueName, String venueAddress, List<SectionSummary> sections) {
            return EventResponse.builder()
                    .id(e.getId())
                    .organizerId(e.getOrganizerId())
                    .venueId(e.getVenueId())
                    .venueName(venueName)
                    .venueAddress(venueAddress)
                    .name(e.getName())
                    .slug(e.getSlug())
                    .category(e.getCategory())
                    .description(e.getDescription())
                    .bannerUrl(e.getBannerUrl())
                    .startTime(e.getStartTime())
                    .endTime(e.getEndTime())
                    .salesStartTime(e.getSalesStartTime())
                    .salesEndTime(e.getSalesEndTime())
                    .waitingRoomEnabled(e.isWaitingRoomEnabled())
                    .maxConcurrentUsers(e.getMaxConcurrentUsers())
                    .maxSeatsPerReservation(e.getMaxSeatsPerReservation())
                    .status(e.getStatus().name())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .sections(sections)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionSummary {
        private UUID id;
        private String name;
        private long price;
        private int capacity;
        private String type;
    }
}
