package space.ducvinh.ticket_booking_service.reservation.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;

public interface SeatClaimRepository extends JpaRepository<SeatClaimEntity, UUID> {

    List<SeatClaimEntity> findBySeatIdIn(List<UUID> seatIds);

    List<SeatClaimEntity> findByReservationId(UUID reservationId);

    @Modifying
    @Query("DELETE FROM SeatClaimEntity sc WHERE sc.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") UUID reservationId);

    @Query("""
            SELECT sc FROM SeatClaimEntity sc
            JOIN SeatEntity s ON s.id = sc.seatId
            JOIN TicketSectionEntity ts ON ts.id = s.sectionId
            WHERE ts.eventId = :eventId AND sc.expiresAt > :now
            """)
    List<SeatClaimEntity> findActiveClaimsByEventId(@Param("eventId") UUID eventId, @Param("now") Instant now);

    @Query("""
            SELECT COUNT(sc) FROM SeatClaimEntity sc
            JOIN SeatEntity s ON s.id = sc.seatId
            JOIN TicketSectionEntity ts ON ts.id = s.sectionId
            WHERE ts.eventId = :eventId AND sc.expiresAt > :now
            """)
    long countActiveClaimsByEventId(@Param("eventId") UUID eventId, @Param("now") Instant now);
}
