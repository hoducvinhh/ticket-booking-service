package space.ducvinh.ticket_booking_service.waitingroom.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.waitingroom.entity.WaitingRoomEntryEntity;
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;

public interface WaitingRoomEntryRepository extends JpaRepository<WaitingRoomEntryEntity, UUID> {
    Optional<WaitingRoomEntryEntity> findByEventIdAndUserId(UUID eventId, UUID userId);
    long countByEventIdAndStatus(UUID eventId, WaitingRoomStatus status);
    long countByStatus(WaitingRoomStatus status);
}
