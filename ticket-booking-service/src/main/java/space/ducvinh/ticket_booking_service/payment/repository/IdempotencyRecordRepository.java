package space.ducvinh.ticket_booking_service.payment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.payment.entity.IdempotencyRecordEntity;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    Optional<IdempotencyRecordEntity> findByUserIdAndEndpointAndIdempotencyKey(
            UUID userId, String endpoint, String idempotencyKey);
}
