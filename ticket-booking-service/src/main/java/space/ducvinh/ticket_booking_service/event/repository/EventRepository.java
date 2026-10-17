package space.ducvinh.ticket_booking_service.event.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;

public interface EventRepository extends JpaRepository<EventEntity, UUID>, JpaSpecificationExecutor<EventEntity> {

    Optional<EventEntity> findByIdAndDeletedFalse(UUID id);

    boolean existsBySlug(String slug);

    Page<EventEntity> findByOrganizerIdAndDeletedFalseOrderByCreatedAtDesc(UUID organizerId, Pageable pageable);

    List<EventEntity> findByStatusAndWaitingRoomEnabledTrueAndDeletedFalse(EventStatus status);

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.deleted = false
              AND e.status = 'PUBLISHED'
              AND e.salesStartTime <= :now
              AND e.salesEndTime > :now
            """)
    List<EventEntity> findPublishedEventsReadyToOpenSales(@Param("now") Instant now);

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.deleted = false
              AND e.status = 'SALES_OPEN'
              AND e.salesEndTime <= :now
            """)
    List<EventEntity> findOpenEventsPastSalesEnd(@Param("now") Instant now);

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.deleted = false
              AND e.status IN ('PUBLISHED', 'SALES_CLOSED')
              AND e.endTime <= :now
            """)
    List<EventEntity> findEventsReadyToComplete(@Param("now") Instant now);
}
