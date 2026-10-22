package space.ducvinh.ticket_booking_service.waitingroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;

@Entity
@Table(name = "waiting_room_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingRoomEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private WaitingRoomStatus status = WaitingRoomStatus.WAITING;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "admitted_at")
    private Instant admittedAt;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @PrePersist
    void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }
}
