package space.ducvinh.ticket_booking_service.reservation.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.config.RabbitMqConfig;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.notification.service.NotificationService;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationWorker {

    private static final int SWEEPER_BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final ReservationExpirationService expirationService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMqConfig.RESERVATION_EXPIRE_QUEUE)
    public void onDelayedExpirationMessage(Map<String, Object> payload) {
        Object rawId = payload.get("reservationId");
        if (rawId == null) {
            return;
        }
        UUID reservationId = UUID.fromString(rawId.toString());
        expirationService.expireReservationIfStillActive(reservationId);
    }

    /**
     * Recovery sweeper to ensure no expired reservation stays held if a RabbitMQ delayed message is lost.
     */
    @Scheduled(fixedDelay = 15000)
    public void sweepExpiredReservations() {
        List<UUID> expiredIds = reservationRepository.findExpiredActiveReservationIds(Instant.now(), SWEEPER_BATCH_SIZE);
        for (UUID id : expiredIds) {
            try {
                expirationService.expireReservationIfStillActive(id);
            } catch (Exception ex) {
                log.warn("Sweeper failed to expire reservation {}: {}", id, ex.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void notifyExpiringReservations() {
        Instant now = Instant.now();
        Instant threshold = now.plusSeconds(120);
        List<ReservationEntity> expiring = reservationRepository.findExpiringSoon(now, threshold);
        for (ReservationEntity r : expiring) {
            String key = "reservation:" + r.getId() + ":reminded";
            try {
                Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofMinutes(5));
                if (Boolean.TRUE.equals(first)) {
                    notificationService.createInAppNotification(
                            r.getUserId(),
                            "RESERVATION_EXPIRING",
                            "Reservation Expiring Soon",
                            "Your seat reservation expires soon. Complete checkout before " + r.getExpiresAt());
                    realtimeBroadcastService.sendUserNotification(
                            r.getUserId(),
                            "RESERVATION_EXPIRING",
                            Map.of(
                                    "reservationId", r.getId().toString(),
                                    "eventId", r.getEventId().toString(),
                                    "expiresAt", r.getExpiresAt().toString()));
                }
            } catch (Exception ex) {
                log.debug("Reminder dispatch error for reservation {}: {}", r.getId(), ex.getMessage());
            }
        }
    }
}
