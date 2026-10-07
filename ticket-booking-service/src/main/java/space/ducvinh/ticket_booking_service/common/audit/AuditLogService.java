package space.ducvinh.ticket_booking_service.common.audit;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID actorId, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        String jsonMetadata = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                jsonMetadata = objectMapper.writeValueAsString(metadata);
            } catch (Exception e) {
                log.warn("Failed to serialize audit metadata for action={}", action, e);
            }
        }
        auditLogRepository.save(AuditLogEntity.builder()
                .actorId(actorId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .metadata(jsonMetadata)
                .build());
    }
}
