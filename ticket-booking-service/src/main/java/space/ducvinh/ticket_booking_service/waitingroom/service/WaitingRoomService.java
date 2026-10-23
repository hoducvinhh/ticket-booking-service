package space.ducvinh.ticket_booking_service.waitingroom.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.common.security.HmacSigningService;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.waitingroom.dto.WaitingRoomDtos;
import space.ducvinh.ticket_booking_service.waitingroom.entity.WaitingRoomEntryEntity;
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;
import space.ducvinh.ticket_booking_service.waitingroom.repository.WaitingRoomEntryRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

    private static final String QUEUE_KEY = "event:%s:queue";
    private static final String QUEUE_SEQ_KEY = "event:%s:queue:seq";
    private static final String ADMITTED_KEY = "event:%s:admitted";
    private static final String ADMISSION_TOKEN_KEY = "admission:%s";

    private final EventRepository eventRepository;
    private final WaitingRoomEntryRepository entryRepository;
    private final StringRedisTemplate redisTemplate;
    private final HmacSigningService hmacSigningService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final AppProperties appProperties;

    @Transactional
    public WaitingRoomDtos.WaitingRoomStatusResponse joinQueue(UUID eventId, UUID userId) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.isWaitingRoomEnabled()) {
            Instant expiresAt = Instant.now().plusSeconds(appProperties.getBooking().getWaitingRoomAdmissionTtlSeconds());
            String token = generateSignedAdmissionToken(eventId, userId, expiresAt);
            return WaitingRoomDtos.WaitingRoomStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .status(WaitingRoomStatus.ADMITTED.name())
                    .queuePosition(0L)
                    .totalWaiting(0L)
                    .estimatedWaitSeconds(0L)
                    .admissionToken(token)
                    .tokenExpiresAt(expiresAt)
                    .build();
        }

        String admittedKey = String.format(ADMITTED_KEY, eventId);
        Double existingAdmittedScore = redisTemplate.opsForZSet().score(admittedKey, userId.toString());
        if (existingAdmittedScore != null && existingAdmittedScore > Instant.now().toEpochMilli()) {
            return buildStatusResponse(eventId, userId);
        }

        String queueKey = String.format(QUEUE_KEY, eventId);
        if (redisTemplate.opsForZSet().rank(queueKey, userId.toString()) == null) {
            Long seq = redisTemplate.opsForValue().increment(String.format(QUEUE_SEQ_KEY, eventId));
            long seqPart = seq != null ? (seq % 10000L) : 0L;
            double score = System.currentTimeMillis() * 10000.0 + seqPart;
            redisTemplate.opsForZSet().add(queueKey, userId.toString(), score);
        }

        WaitingRoomEntryEntity entry = entryRepository.findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> WaitingRoomEntryEntity.builder().eventId(eventId).userId(userId).build());
        entry.setStatus(WaitingRoomStatus.WAITING);
        entry.setJoinedAt(Instant.now());
        entryRepository.save(entry);

        admitBatchForEvent(event);
        return buildStatusResponse(eventId, userId);
    }

    @Transactional(readOnly = true)
    public WaitingRoomDtos.WaitingRoomStatusResponse getStatus(UUID eventId, UUID userId) {
        eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return buildStatusResponse(eventId, userId);
    }

    @Transactional
    public void leaveQueue(UUID eventId, UUID userId) {
        redisTemplate.opsForZSet().remove(String.format(QUEUE_KEY, eventId), userId.toString());
        redisTemplate.opsForZSet().remove(String.format(ADMITTED_KEY, eventId), userId.toString());
        entryRepository.findByEventIdAndUserId(eventId, userId).ifPresent(entry -> {
            entry.setStatus(WaitingRoomStatus.LEFT);
            entryRepository.save(entry);
        });
    }

    public void validateAdmissionToken(UUID eventId, UUID userId, String token) {
        if (token == null || token.isBlank() || !hmacSigningService.verifyAdmissionToken(token)) {
            throw new ForbiddenException("Valid waiting room admission token is required");
        }
        String payload = hmacSigningService.extractAdmissionPayload(token);
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw new ForbiddenException("Malformed admission token");
        }
        String tokenId = parts[0];
        UUID tokenEventId = UUID.fromString(parts[1]);
        UUID tokenUserId = UUID.fromString(parts[2]);
        long expiresAtEpochSec = Long.parseLong(parts[3]);

        if (!tokenEventId.equals(eventId) || !tokenUserId.equals(userId)) {
            throw new ForbiddenException("Admission token does not match event or user");
        }
        if (Instant.now().getEpochSecond() > expiresAtEpochSec) {
            throw new ForbiddenException("Admission token has expired");
        }
        String redisVal = redisTemplate.opsForValue().get(String.format(ADMISSION_TOKEN_KEY, tokenId));
        if (redisVal == null) {
            WaitingRoomEntryEntity entry = entryRepository.findByEventIdAndUserId(eventId, userId)
                    .orElseThrow(() -> new ForbiddenException("Admission session not found"));
            if (entry.getStatus() != WaitingRoomStatus.ADMITTED
                    || entry.getTokenExpiresAt() == null
                    || entry.getTokenExpiresAt().isBefore(Instant.now())) {
                throw new ForbiddenException("Admission token is no longer active");
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void processWaitingRooms() {
        List<EventEntity> events = eventRepository.findByStatusAndWaitingRoomEnabledTrueAndDeletedFalse(EventStatus.SALES_OPEN);
        for (EventEntity event : events) {
            try {
                admitBatchForEvent(event);
            } catch (Exception ex) {
                log.warn("Waiting room scheduler error for event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }

    private void admitBatchForEvent(EventEntity event) {
        UUID eventId = event.getId();
        String admittedKey = String.format(ADMITTED_KEY, eventId);
        String queueKey = String.format(QUEUE_KEY, eventId);

        long nowMs = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(admittedKey, 0, nowMs);

        Long currentAdmitted = redisTemplate.opsForZSet().zCard(admittedKey);
        long activeCount = currentAdmitted != null ? currentAdmitted : 0L;
        int slotsAvailable = (int) Math.max(0, event.getMaxConcurrentUsers() - activeCount);
        if (slotsAvailable <= 0) {
            return;
        }

        Set<String> nextUsers = redisTemplate.opsForZSet().range(queueKey, 0, slotsAvailable - 1L);
        if (nextUsers == null || nextUsers.isEmpty()) {
            return;
        }

        long ttlSec = appProperties.getBooking().getWaitingRoomAdmissionTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(ttlSec);

        for (String userIdStr : nextUsers) {
            redisTemplate.opsForZSet().remove(queueKey, userIdStr);
            redisTemplate.opsForZSet().add(admittedKey, userIdStr, expiresAt.toEpochMilli());

            UUID uid = UUID.fromString(userIdStr);
            String token = generateSignedAdmissionToken(eventId, uid, expiresAt);

            entryRepository.findByEventIdAndUserId(eventId, uid).ifPresent(entry -> {
                entry.setStatus(WaitingRoomStatus.ADMITTED);
                entry.setAdmittedAt(Instant.now());
                entry.setTokenExpiresAt(expiresAt);
                entryRepository.save(entry);
            });

            realtimeBroadcastService.sendUserNotification(uid, "USER_ADMITTED", Map.of(
                    "eventId", eventId.toString(),
                    "admissionToken", token,
                    "tokenExpiresAt", expiresAt.toString()
            ));
        }

        Long remaining = redisTemplate.opsForZSet().zCard(queueKey);
        realtimeBroadcastService.broadcastWaitingRoomEvent(eventId, "QUEUE_POSITION_UPDATED", Map.of(
                "totalWaiting", remaining != null ? remaining : 0L
        ));
    }

    private WaitingRoomDtos.WaitingRoomStatusResponse buildStatusResponse(UUID eventId, UUID userId) {
        String queueKey = String.format(QUEUE_KEY, eventId);
        String admittedKey = String.format(ADMITTED_KEY, eventId);

        Long total = redisTemplate.opsForZSet().zCard(queueKey);
        long totalWaiting = total != null ? total : 0L;

        Double admittedScore = redisTemplate.opsForZSet().score(admittedKey, userId.toString());
        if (admittedScore != null && admittedScore > Instant.now().toEpochMilli()) {
            Instant expiresAt = Instant.ofEpochMilli(admittedScore.longValue());
            String token = generateSignedAdmissionToken(eventId, userId, expiresAt);
            return WaitingRoomDtos.WaitingRoomStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .status(WaitingRoomStatus.ADMITTED.name())
                    .queuePosition(0L)
                    .totalWaiting(totalWaiting)
                    .estimatedWaitSeconds(0L)
                    .admissionToken(token)
                    .tokenExpiresAt(expiresAt)
                    .build();
        }

        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        if (rank != null) {
            long pos = rank + 1;
            return WaitingRoomDtos.WaitingRoomStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .status(WaitingRoomStatus.WAITING.name())
                    .queuePosition(pos)
                    .totalWaiting(totalWaiting)
                    .estimatedWaitSeconds(Math.max(5L, pos * 2L))
                    .build();
        }

        return entryRepository.findByEventIdAndUserId(eventId, userId)
                .map(e -> WaitingRoomDtos.WaitingRoomStatusResponse.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .status(e.getStatus().name())
                        .totalWaiting(totalWaiting)
                        .tokenExpiresAt(e.getTokenExpiresAt())
                        .build())
                .orElse(WaitingRoomDtos.WaitingRoomStatusResponse.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .status(WaitingRoomStatus.LEFT.name())
                        .totalWaiting(totalWaiting)
                        .build());
    }

    private String generateSignedAdmissionToken(UUID eventId, UUID userId, Instant expiresAt) {
        String tokenId = UUID.randomUUID().toString();
        String payload = tokenId + "|" + eventId + "|" + userId + "|" + expiresAt.getEpochSecond();
        String token = hmacSigningService.signAdmissionPayload(payload);
        long ttl = Math.max(30L, Duration.between(Instant.now(), expiresAt).getSeconds());
        redisTemplate.opsForValue().set(String.format(ADMISSION_TOKEN_KEY, tokenId), userId.toString(), Duration.ofSeconds(ttl));
        return token;
    }
}
