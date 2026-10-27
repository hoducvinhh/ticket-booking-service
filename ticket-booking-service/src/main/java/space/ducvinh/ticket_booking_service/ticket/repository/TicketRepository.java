package space.ducvinh.ticket_booking_service.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    Page<TicketEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<TicketEntity> findByOrderId(UUID orderId);

    List<TicketEntity> findByEventIdAndStatusIn(UUID eventId, List<TicketStatus> statuses);

    boolean existsBySeatIdAndStatusIn(UUID seatId, List<TicketStatus> statuses);

    long countByEventIdAndStatusIn(UUID eventId, List<TicketStatus> statuses);

    long countByEventIdAndStatus(UUID eventId, TicketStatus status);

    Optional<TicketEntity> findByTicketCode(String ticketCode);

    @Modifying
    @Query("""
            UPDATE TicketEntity t
            SET t.status = 'CHECKED_IN', t.checkedInAt = :now
            WHERE t.id = :ticketId AND t.eventId = :eventId AND t.status = 'VALID'
            """)
    int markCheckedInAtomically(
            @Param("ticketId") UUID ticketId,
            @Param("eventId") UUID eventId,
            @Param("now") Instant now);
}
