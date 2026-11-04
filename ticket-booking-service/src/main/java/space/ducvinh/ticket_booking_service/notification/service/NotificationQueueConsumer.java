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
            case "USER_REGISTERED" -> "Welcome to Event Ticketing!";
            case "ORDER_CREATED" -> "Order Created";
            case "PAYMENT_SUCCEEDED" -> "Payment Successful";
            case "TICKETS_ISSUED" -> "Your E-Tickets Are Ready";
            case "EVENT_CANCELLED" -> "Event Cancelled & Refund Initiated";
            case "PAYMENT_EXPIRED_REFUND_NEEDED" -> "Reservation Expired During Payment";
            case "PASSWORD_RESET_REQUESTED" -> "Password Reset Request";
            default -> "Ticketing Update";
        };
    }

    private String resolveContent(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "USER_REGISTERED" -> "Your account has been registered successfully.";
            case "ORDER_CREATED" -> "Order " + payload.getOrDefault("orderCode", "") + " has been created. Please complete payment before expiration.";
            case "PAYMENT_SUCCEEDED" -> "Payment received for order " + payload.getOrDefault("orderCode", "") + ".";
            case "TICKETS_ISSUED" -> "Issued " + payload.getOrDefault("ticketCount", 1) + " e-ticket(s) for order " + payload.getOrDefault("orderCode", "") + ".";
            case "EVENT_CANCELLED" -> "Event " + payload.getOrDefault("eventName", "") + " has been cancelled. Your refund is being processed.";
            case "PAYMENT_EXPIRED_REFUND_NEEDED" -> "Payment succeeded after reservation expiration for order " + payload.getOrDefault("orderCode", "") + ". A refund has been initiated.";
            case "PASSWORD_RESET_REQUESTED" -> "Use this reset token to set a new password: " + payload.getOrDefault("resetToken", "");
            default -> "Notification event: " + eventType;
        };
    }
}
