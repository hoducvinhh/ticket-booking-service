package space.ducvinh.ticket_booking_service.notification.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.config.RabbitMqConfig;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationQueueConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void onNotificationEvent(Map<String, Object> payload) {
        String eventId = String.valueOf(payload.getOrDefault("outboxEventId", ""));
        if (!acquireIdempotencyKey("consumer:notif:" + eventId)) {
            return;
        }
        String eventType = String.valueOf(payload.getOrDefault("eventType", ""));
        Object rawUserId = payload.get("userId");
        if (rawUserId == null || rawUserId.toString().isBlank()) {
            return;
        }
        UUID userId = UUID.fromString(rawUserId.toString());
        String title = resolveTitle(eventType);
        String content = resolveContent(eventType, payload);
        notificationService.createInAppNotification(userId, eventType, title, content);
    }

    @RabbitListener(queues = RabbitMqConfig.EMAIL_QUEUE)
    public void onEmailEvent(Map<String, Object> payload) {
        String eventId = String.valueOf(payload.getOrDefault("outboxEventId", ""));
        if (!acquireIdempotencyKey("consumer:email:" + eventId)) {
            return;
        }
        String email = String.valueOf(payload.getOrDefault("email", ""));
        String eventType = String.valueOf(payload.getOrDefault("eventType", ""));
        if (email.isBlank()) {
            return;
        }
        String subject = "[Ticketing] " + resolveTitle(eventType);
        String body = resolveContent(eventType, payload);

        if (mailHost == null || mailHost.isBlank()) {
            log.info("Simulated Email -> to={}, subject={}, body={}", email, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception ex) {
            log.warn("Failed sending email to {}: {}", email, ex.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMqConfig.TICKET_QUEUE)
    public void onTicketEvent(Map<String, Object> payload) {
        log.debug("Processed ticket background job: {}", payload.get("eventType"));
    }

    @RabbitListener(queues = RabbitMqConfig.ANALYTICS_QUEUE)
    public void onAnalyticsEvent(Map<String, Object> payload) {
        log.debug("Recorded analytics event: {}", payload.get("eventType"));
    }

    private boolean acquireIdempotencyKey(String key) {
        if (key.endsWith(":")) {
            return true;
        }
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            return true;
        }
    }

    private String resolveTitle(String eventType) {
        return switch (eventType) {
            case "USER_REGISTERED" -> "Chào mừng bạn đến với FlashTix!";
            case "ORDER_CREATED" -> "Đã tạo đơn hàng mới";
            case "PAYMENT_SUCCEEDED" -> "Thanh toán thành công";
            case "TICKETS_ISSUED" -> "Vé điện tử của bạn đã sẵn sàng";
            case "EVENT_REMINDER" -> "Nhắc lịch sự kiện sắp diễn ra";
            case "EVENT_CANCELLED" -> "Sự kiện đã hủy & Đã tạo yêu cầu hoàn tiền";
            case "REFUND_SUCCEEDED" -> "Hoàn tiền thành công";
            case "PAYMENT_EXPIRED_REFUND_NEEDED" -> "Đơn thanh toán sau khi hết hạn giữ chỗ";
            case "PASSWORD_RESET_REQUESTED" -> "Yêu cầu đặt lại mật khẩu";
            default -> "Cập nhật từ hệ thống FlashTix";
        };
    }

    private String resolveContent(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "USER_REGISTERED" -> "Tài khoản của bạn đã được đăng ký thành công. Chúc bạn có trải nghiệm đặt vé tuyệt vời!";
            case "ORDER_CREATED" -> "Đơn hàng " + payload.getOrDefault("orderCode", "") + " đã được tạo. Vui lòng hoàn tất thanh toán trước khi hết thời gian giữ ghế.";
            case "PAYMENT_SUCCEEDED" -> "Hệ thống đã ghi nhận thanh toán thành công cho đơn hàng " + payload.getOrDefault("orderCode", "") + ".";
            case "TICKETS_ISSUED" -> "Đã phát hành " + payload.getOrDefault("ticketCount", 1) + " vé điện tử cho đơn hàng " + payload.getOrDefault("orderCode", "") + ". Bạn có thể xem mã QR tại mục Vé của tôi.";
            case "EVENT_REMINDER" -> "Sự kiện " + payload.getOrDefault("eventName", "") + " sẽ diễn ra trong vòng 24 giờ tới. Hãy chuẩn bị sẵn mã QR khi đến cổng soát vé nhé.";
            case "EVENT_CANCELLED" -> "Sự kiện " + payload.getOrDefault("eventName", "") + " đã bị hủy. Yêu cầu hoàn tiền của bạn đang được hệ thống xử lý.";
            case "REFUND_SUCCEEDED" -> "Khoản hoàn tiền cho đơn hàng " + payload.getOrDefault("orderCode", "") + " đã được xử lý thành công.";
            case "PAYMENT_EXPIRED_REFUND_NEEDED" -> "Thanh toán cho đơn hàng " + payload.getOrDefault("orderCode", "") + " được ghi nhận sau khi hết hạn giữ ghế. Hệ thống đã chuyển đơn sang trạng thái chờ hoàn tiền.";
            case "PASSWORD_RESET_REQUESTED" -> "Sử dụng mã xác thực sau để đặt lại mật khẩu mới: " + payload.getOrDefault("resetToken", "");
            default -> "Cập nhật sự kiện hệ thống: " + eventType;
        };
    }
}
