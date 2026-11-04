package space.ducvinh.ticket_booking_service.notification.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.notification.dto.NotificationDtos;
import space.ducvinh.ticket_booking_service.notification.entity.NotificationEntity;
import space.ducvinh.ticket_booking_service.notification.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimeBroadcastService realtimeBroadcastService;

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> listMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDtos.NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCountResponse countUnread(UUID userId) {
        return NotificationDtos.UnreadCountResponse.builder()
                .unreadCount(notificationRepository.countByUserIdAndReadFalse(userId))
                .build();
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        SecurityUtils.requireOwnerOrAdmin(entity.getUserId());
        entity.setRead(true);
        notificationRepository.save(entity);
    }

    @Transactional
    public void createInAppNotification(UUID userId, String type, String title, String content) {
        NotificationEntity saved = notificationRepository.save(NotificationEntity.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .channel("IN_APP")
                .build());
        realtimeBroadcastService.sendUserNotification(userId, type, Map.of(
                "notificationId", saved.getId().toString(),
                "title", title,
                "content", content
        ));
    }
}
