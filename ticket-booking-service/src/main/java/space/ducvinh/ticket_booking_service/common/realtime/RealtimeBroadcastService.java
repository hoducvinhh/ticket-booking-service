package space.ducvinh.ticket_booking_service.common.realtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastSeatEvent(UUID eventId, String eventType, List<UUID> seatIds, UUID reservationId) {
        RealtimeEventMessage msg = RealtimeEventMessage.builder()
                .type(eventType)
                .eventId(eventId)
                .payload(Map.of(
                        "seatIds", seatIds,
                        "reservationId", reservationId != null ? reservationId.toString() : ""))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/seats", msg);
    }

    public void broadcastWaitingRoomEvent(UUID eventId, String eventType, Map<String, Object> payload) {
        RealtimeEventMessage msg = RealtimeEventMessage.builder()
                .type(eventType)
                .eventId(eventId)
                .payload(payload)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/waiting-room", msg);
    }

    public void sendUserNotification(UUID userId, String type, Map<String, Object> payload) {
        RealtimeEventMessage msg = RealtimeEventMessage.builder()
                .type(type)
                .payload(payload)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", msg);
    }
}
