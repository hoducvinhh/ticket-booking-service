package space.ducvinh.ticket_booking_service.common.outbox;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void enqueue(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        String jsonPayload = objectMapper.writeValueAsString(payload);
        outboxEventRepository.save(OutboxEventEntity.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(jsonPayload)
                .status("PENDING")
                .build());
    }
}
