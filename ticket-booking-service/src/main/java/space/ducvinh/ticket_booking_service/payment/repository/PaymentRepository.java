package space.ducvinh.ticket_booking_service.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.ducvinh.ticket_booking_service.payment.entity.PaymentEntity;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    Optional<PaymentEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(PaymentStatus status);
}
