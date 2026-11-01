package space.ducvinh.ticket_booking_service.payment.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.payment.entity.WebhookEventEntity;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID> {

    Optional<WebhookEventEntity> findByProviderAndProviderEventId(String provider, String providerEventId);

    @Modifying
    @Query(value = """
            INSERT INTO webhook_events (id, provider, provider_event_id, payload_hash, raw_payload, status, received_at)
            VALUES (:id, :provider, :providerEventId, :payloadHash, CAST(:rawPayload AS jsonb), 'RECEIVED', :receivedAt)
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("payloadHash") String payloadHash,
            @Param("rawPayload") String rawPayload,
            @Param("receivedAt") Instant receivedAt);

    long countByStatus(String status);
}
