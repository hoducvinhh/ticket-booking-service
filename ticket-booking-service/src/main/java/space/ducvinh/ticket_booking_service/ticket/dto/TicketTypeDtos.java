package space.ducvinh.ticket_booking_service.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;

public class TicketTypeDtos {

    @Data
    public static class CreateTicketTypeRequest {
        private UUID sectionId;
        @NotBlank
        private String name;
        @Min(0)
        private long price;
        @Min(1)
        private int totalQuantity;
        private String saleMode = "GENERAL_ADMISSION";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketTypeResponse {
        private UUID id;
        private UUID eventId;
        private UUID sectionId;
        private String name;
        private long price;
        private int totalQuantity;
        private int soldQuantity;
        private int remainingQuantity;
        private String saleMode;

        public static TicketTypeResponse from(TicketTypeEntity t) {
            return TicketTypeResponse.builder()
                    .id(t.getId())
                    .eventId(t.getEventId())
                    .sectionId(t.getSectionId())
                    .name(t.getName())
                    .price(t.getPrice())
                    .totalQuantity(t.getTotalQuantity())
                    .soldQuantity(t.getSoldQuantity())
                    .remainingQuantity(Math.max(0, t.getTotalQuantity() - t.getSoldQuantity()))
                    .saleMode(t.getSaleMode())
                    .build();
        }
    }
}
