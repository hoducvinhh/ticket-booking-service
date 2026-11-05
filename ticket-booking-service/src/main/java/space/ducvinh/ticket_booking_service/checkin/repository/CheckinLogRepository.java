package space.ducvinh.ticket_booking_service.checkin.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.checkin.entity.CheckinLogEntity;

public interface CheckinLogRepository extends JpaRepository<CheckinLogEntity, UUID> {
    Page<CheckinLogEntity> findByEventIdOrderByCheckedInAtDesc(UUID eventId, Pageable pageable);
    long countByEventId(UUID eventId);
}
