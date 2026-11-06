package space.ducvinh.ticket_booking_service.reporting.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ReportingDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizerEventStatsResponse {
        private UUID eventId;
        private long totalTickets;
        private long soldTickets;
        private long heldTickets;
        private long availableTickets;
        private long totalOrders;
        private long totalRevenue;
        private double paymentSuccessRate;
        private long checkinCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOverviewStatsResponse {
        private long totalUsers;
        private long totalEvents;
        private long totalOrders;
        private long totalPayments;
        private long totalRevenue;
        private long failedPayments;
        private long failedWebhooks;
        private long waitingRoomQueueBacklog;
        private long reservationExpirationBacklog;
    }
}
