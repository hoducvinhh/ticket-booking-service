package space.ducvinh.ticket_booking_service.venue.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;

public interface SeatRepository extends JpaRepository<SeatEntity, UUID> {

    List<SeatEntity> findBySectionIdInOrderByRowLabelAscSeatNumberAsc(List<UUID> sectionIds);

    List<SeatEntity> findBySectionIdOrderByRowLabelAscSeatNumberAsc(UUID sectionId);

    boolean existsBySectionIdAndSeatCodeIgnoreCase(UUID sectionId, String seatCode);

    /**
     * Locks seats in deterministic UUID order to prevent deadlocks under concurrent multi-seat bookings.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatEntity s WHERE s.id IN :ids ORDER BY s.id ASC")
    List<SeatEntity> findAllByIdInForUpdateOrderById(@Param("ids") List<UUID> ids);

    @Query("""
            SELECT s FROM SeatEntity s
            JOIN TicketSectionEntity ts ON ts.id = s.sectionId
            WHERE ts.eventId = :eventId
            ORDER BY ts.name ASC, s.rowLabel ASC, s.seatNumber ASC
            """)
    List<SeatEntity> findAllByEventId(@Param("eventId") UUID eventId);

    @Query("""
            SELECT COUNT(s) FROM SeatEntity s
            JOIN TicketSectionEntity ts ON ts.id = s.sectionId
            WHERE ts.eventId = :eventId
            """)
    long countByEventId(@Param("eventId") UUID eventId);
}
