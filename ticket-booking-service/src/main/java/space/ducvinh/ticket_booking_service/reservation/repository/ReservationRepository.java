package space.ducvinh.ticket_booking_service.reservation.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    Page<ReservationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationEntity r WHERE r.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT id FROM reservations
            WHERE status = 'ACTIVE' AND expires_at <= :now
            ORDER BY expires_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findExpiredActiveReservationIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query("""
            SELECT r FROM ReservationEntity r
            WHERE r.status = 'ACTIVE' AND r.expiresAt > :now AND r.expiresAt <= :threshold
            """)
    List<ReservationEntity> findExpiringSoon(@Param("now") Instant now, @Param("threshold") Instant threshold);

    long countByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    boolean existsByEventId(UUID eventId);
}
