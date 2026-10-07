package space.ducvinh.ticket_booking_service.common.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND retry_count < :maxRetries
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> fetchPendingBatchForUpdate(
            @Param("batchSize") int batchSize,
            @Param("maxRetries") int maxRetries);

    long countByStatus(String status);
}
