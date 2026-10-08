package space.ducvinh.ticket_booking_service.common.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeEventMessage {
    private String type;
    private UUID eventId;
    private Map<String, Object> payload;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
