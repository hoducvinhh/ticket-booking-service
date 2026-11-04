package space.ducvinh.ticket_booking_service.checkin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "checkin_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    @Column(name = "device_info")
    private String deviceInfo;

    @PrePersist
    void onCreate() {
        if (this.checkedInAt == null) {
            this.checkedInAt = Instant.now();
        }
    }
}
