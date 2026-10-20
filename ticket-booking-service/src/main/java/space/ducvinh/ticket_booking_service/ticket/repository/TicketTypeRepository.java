package space.ducvinh.ticket_booking_service.ticket.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;

public interface TicketTypeRepository extends JpaRepository<TicketTypeEntity, UUID> {
    List<TicketTypeEntity> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketTypeEntity t WHERE t.id = :id")
    Optional<TicketTypeEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE TicketTypeEntity t
            SET t.soldQuantity = t.soldQuantity + :qty
            WHERE t.id = :id AND t.soldQuantity + :qty <= t.totalQuantity
            """)
    int incrementSoldQuantityAtomically(@Param("id") UUID id, @Param("qty") int qty);
}
