package space.ducvinh.ticket_booking_service.event.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void sendUpcomingEventReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(24, ChronoUnit.HOURS);

        List<EventEntity> upcoming = eventRepository.findAll().stream()
                .filter(e -> !e.isDeleted()
                        && (e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SALES_OPEN || e.getStatus() == EventStatus.SALES_CLOSED)
                        && e.getStartTime().isAfter(now)
                        && !e.getStartTime().isAfter(windowEnd))
                .toList();

        for (EventEntity event : upcoming) {
            List<TicketEntity> validTickets = ticketRepository.findByEventIdAndStatusIn(
                    event.getId(), List.of(TicketStatus.VALID));
            List<UUID> userIds = validTickets.stream().map(TicketEntity::getUserId).distinct().toList();

            for (UUID userId : userIds) {
                String key = "event:" + event.getId() + ":reminded:" + userId;
                try {
                    Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(36));
                    if (!Boolean.TRUE.equals(first)) {
                        continue;
                    }
                } catch (Exception ex) {
                    log.debug("Reminder Redis check skipped: {}", ex.getMessage());
                    continue;
                }

                String email = userRepository.findById(userId).map(UserEntity::getEmail).orElse("");
                outboxService.enqueue("EVENT", event.getId(), "EVENT_REMINDER", Map.of(
                        "eventId", event.getId().toString(),
                        "eventName", event.getName(),
                        "startTime", event.getStartTime().toString(),
                        "userId", userId.toString(),
                        "email", email
                ));
            }
        }
    }
}
