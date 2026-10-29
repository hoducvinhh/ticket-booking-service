package space.ducvinh.ticket_booking_service.order.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByReservationId(UUID reservationId);

    Optional<OrderEntity> findByOrderCode(String orderCode);

    Page<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<OrderEntity> findByEventIdAndStatus(UUID eventId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByEventId(UUID eventId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderEntity o WHERE o.eventId = :eventId AND o.status = 'PAID'")
    long sumPaidRevenueByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderEntity o WHERE o.status = 'PAID'")
    long sumTotalPaidRevenue();
}
