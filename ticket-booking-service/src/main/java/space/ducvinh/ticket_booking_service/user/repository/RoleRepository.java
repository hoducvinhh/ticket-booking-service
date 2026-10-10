package space.ducvinh.ticket_booking_service.user.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import space.ducvinh.ticket_booking_service.user.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
}
