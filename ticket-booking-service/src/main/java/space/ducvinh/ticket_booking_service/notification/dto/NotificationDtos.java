package space.ducvinh.ticket_booking_service.notification.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.notification.entity.NotificationEntity;

public class NotificationDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResponse {
        private UUID id;
        private UUID userId;
        private String type;
        private String title;
        private String content;
        private String channel;
        private boolean read;
        private Instant createdAt;

        public static NotificationResponse from(NotificationEntity n) {
            return NotificationResponse.builder()
                    .id(n.getId())
                    .userId(n.getUserId())
                    .type(n.getType())
                    .title(n.getTitle())
                    .content(n.getContent())
                    .channel(n.getChannel())
                    .read(n.isRead())
                    .createdAt(n.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnreadCountResponse {
        private long unreadCount;
    }
}
