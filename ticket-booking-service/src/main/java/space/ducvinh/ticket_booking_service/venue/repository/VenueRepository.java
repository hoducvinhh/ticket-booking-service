package space.ducvinh.ticket_booking_service.venue.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;

public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {
    Page<VenueEntity> findByOrganizerId(UUID organizerId, Pageable pageable);
    List<VenueEntity> findByOrganizerId(UUID organizerId);
}
