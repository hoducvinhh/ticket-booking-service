package space.ducvinh.ticket_booking_service.venue.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;

public interface TicketSectionRepository extends JpaRepository<TicketSectionEntity, UUID> {
    List<TicketSectionEntity> findByEventIdOrderByNameAsc(UUID eventId);
    boolean existsByEventIdAndNameIgnoreCase(UUID eventId, String name);
}
