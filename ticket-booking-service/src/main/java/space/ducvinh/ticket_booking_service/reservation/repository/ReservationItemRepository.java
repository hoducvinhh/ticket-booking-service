package space.ducvinh.ticket_booking_service.reservation.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationItemEntity;

public interface ReservationItemRepository extends JpaRepository<ReservationItemEntity, UUID> {
    List<ReservationItemEntity> findByReservationId(UUID reservationId);

    @Query("""
            SELECT COALESCE(SUM(ri.quantity), 0)
            FROM ReservationItemEntity ri
            JOIN ReservationEntity r ON r.id = ri.reservationId
            WHERE ri.ticketTypeId = :ticketTypeId
              AND r.status = 'ACTIVE'
              AND r.expiresAt > :now
            """)
    long sumActiveReservedQuantity(@Param("ticketTypeId") UUID ticketTypeId, @Param("now") Instant now);
}
