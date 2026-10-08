package space.ducvinh.ticket_booking_service.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.config.RabbitMqConfig;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> batch = outboxEventRepository.fetchPendingBatchForUpdate(BATCH_SIZE, MAX_RETRIES);
        for (OutboxEventEntity event : batch) {
            try {
                Map<String, Object> body = objectMapper.readValue(
                        event.getPayload(), new TypeReference<Map<String, Object>>() {});
                body.put("outboxEventId", event.getId().toString());
                body.put("eventType", event.getEventType());
                body.put("aggregateId", event.getAggregateId().toString());

                String routingKey = resolveRoutingKey(event.getEventType());
                rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, body);

                // Also route user-facing business events to email & notification queues
                if (shouldNotify(event.getEventType())) {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, "notification.event", body);
                    rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, "email.event", body);
                }

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(ex.getMessage());
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus("FAILED");
                }
            }
        }
    }

    private String resolveRoutingKey(String eventType) {
        return switch (eventType) {
            case "TICKETS_ISSUED" -> "ticket.issued";
            case "ANALYTICS_RECORD" -> "analytics.record";
            default -> "analytics." + eventType.toLowerCase();
        };
    }

    private boolean shouldNotify(String eventType) {
        return switch (eventType) {
            case "USER_REGISTERED", "ORDER_CREATED", "PAYMENT_SUCCEEDED", "TICKETS_ISSUED",
                    "RESERVATION_EXPIRING", "EVENT_REMINDER", "EVENT_CANCELLED",
                    "REFUND_SUCCEEDED", "PAYMENT_EXPIRED_REFUND_NEEDED" -> true;
            default -> false;
        };
    }
}
