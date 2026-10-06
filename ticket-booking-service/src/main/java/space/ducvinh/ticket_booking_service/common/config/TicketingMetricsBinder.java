package space.ducvinh.ticket_booking_service.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxEventRepository;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;
import space.ducvinh.ticket_booking_service.payment.repository.PaymentRepository;
import space.ducvinh.ticket_booking_service.payment.repository.WebhookEventRepository;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;
import space.ducvinh.ticket_booking_service.waitingroom.repository.WaitingRoomEntryRepository;

@Component
@RequiredArgsConstructor
public class TicketingMetricsBinder {

    private final MeterRegistry meterRegistry;
    private final ReservationRepository reservationRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final WaitingRoomEntryRepository waitingRoomEntryRepository;

    @PostConstruct
    public void bindMetrics() {
        Gauge.builder("ticketing.reservation.expiration.backlog", reservationRepository,
                        r -> r.countByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, Instant.now()))
                .description("Expired ACTIVE reservations pending cleanup")
                .register(meterRegistry);

        Gauge.builder("ticketing.webhook.failures", webhookEventRepository,
                        w -> w.countByStatus("FAILED"))
                .description("Failed webhook events")
                .register(meterRegistry);

        Gauge.builder("ticketing.payment.failures", paymentRepository,
                        p -> p.countByStatus(PaymentStatus.FAILED))
                .description("Failed payment attempts")
                .register(meterRegistry);

        Gauge.builder("ticketing.outbox.failed", outboxEventRepository,
                        o -> o.countByStatus("FAILED"))
                .description("Failed outbox jobs")
                .register(meterRegistry);

        Gauge.builder("ticketing.waitingroom.backlog", waitingRoomEntryRepository,
                        w -> w.countByStatus(WaitingRoomStatus.WAITING))
                .description("Users currently waiting in virtual waiting rooms")
                .register(meterRegistry);
    }
}
