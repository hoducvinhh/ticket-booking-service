package space.ducvinh.ticket_booking_service.notification.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.notification.dto.NotificationDtos;
import space.ducvinh.ticket_booking_service.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/users/me/notifications")
    public ApiResponse<PageResponse<NotificationDtos.NotificationResponse>> listMyNotifications(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                notificationService.listMyNotifications(SecurityUtils.requireCurrentUserId(), pageable)));
    }

    @GetMapping("/users/me/notifications/unread-count")
    public ApiResponse<NotificationDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notificationService.countUnread(SecurityUtils.requireCurrentUserId()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ApiResponse.okMessage("Marked as read");
    }
}
