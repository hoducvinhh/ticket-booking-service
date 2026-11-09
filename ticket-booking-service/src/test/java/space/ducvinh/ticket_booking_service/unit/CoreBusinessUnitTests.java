package space.ducvinh.ticket_booking_service.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogService;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.security.HmacSigningService;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.event.dto.EventDtos;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.event.repository.EventStaffRepository;
import space.ducvinh.ticket_booking_service.event.service.EventService;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import space.ducvinh.ticket_booking_service.venue.repository.VenueRepository;

class CoreBusinessUnitTests {

    private AppProperties appProperties;
    private HmacSigningService hmacSigningService;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getSecurity().setJwtSecret("test-jwt-secret-key-at-least-32-bytes-long-2026");
        appProperties.getSecurity().setQrHmacSecret("test-qr-hmac-secret-at-least-32-bytes-long-2026");
        appProperties.getSecurity().setAdmissionTokenSecret("test-admission-secret-at-least-32-bytes-long");
        hmacSigningService = new HmacSigningService(appProperties);
        jwtTokenProvider = new JwtTokenProvider(appProperties);
    }

    @Test
    void qrTokenSigningAndTamperDetection() {
        String payload = UUID.randomUUID() + "|" + UUID.randomUUID() + "|" + UUID.randomUUID() + "|1764000000";
        String token = hmacSigningService.signQrPayload(payload);
        assertTrue(hmacSigningService.verifyQrToken(token));
        assertEquals(payload, hmacSigningService.extractQrPayload(token));

        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertFalse(hmacSigningService.verifyQrToken(tampered));
    }

    @Test
    void jwtTokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        String jwt = jwtTokenProvider.generateAccessToken(userId, "test@example.com", Set.of("CUSTOMER"));
        var claims = jwtTokenProvider.parseToken(jwt);
        var principal = jwtTokenProvider.toPrincipal(claims);
        assertEquals(userId, principal.getUserId());
        assertEquals("test@example.com", principal.getEmail());
        assertTrue(principal.hasRole("CUSTOMER"));
    }

    @Test
    void createEventRejectsInvalidSchedule() {
        EventRepository eventRepository = mock(EventRepository.class);
        VenueRepository venueRepository = mock(VenueRepository.class);
        EventService eventService = new EventService(
                eventRepository,
                venueRepository,
                mock(TicketSectionRepository.class),
                mock(EventStaffRepository.class),
                mock(ReservationRepository.class),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(OutboxService.class),
                mock(AuditLogService.class),
                appProperties);

        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(VenueEntity.builder().id(venueId).build()));

        Instant now = Instant.now();
        EventDtos.CreateEventRequest req = new EventDtos.CreateEventRequest();
        req.setVenueId(venueId);
        req.setName("Broken Schedule Concert");
        req.setStartTime(now.plus(2, ChronoUnit.DAYS));
        req.setEndTime(now.plus(1, ChronoUnit.DAYS)); // invalid: end before start
        req.setSalesStartTime(now);
        req.setSalesEndTime(now.plus(12, ChronoUnit.HOURS));

        assertThrows(BusinessRuleViolationException.class,
                () -> eventService.createEvent(UUID.randomUUID(), req));
    }
}
