package space.ducvinh.ticket_booking_service.event.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.event.entity.EventStaffEntity;

public interface EventStaffRepository extends JpaRepository<EventStaffEntity, UUID> {
    boolean existsByEventIdAndStaffId(UUID eventId, UUID staffId);
    List<EventStaffEntity> findByStaffId(UUID staffId);
    List<EventStaffEntity> findByEventId(UUID eventId);
}
