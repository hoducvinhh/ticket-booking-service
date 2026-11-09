package space.ducvinh.ticket_booking_service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import space.ducvinh.ticket_booking_service.AbstractIntegrationTest;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.reservation.dto.ReservationDtos;
import space.ducvinh.ticket_booking_service.reservation.service.ReservationService;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.RoleName;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.RoleRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import space.ducvinh.ticket_booking_service.venue.repository.VenueRepository;
import space.ducvinh.ticket_booking_service.waitingroom.service.WaitingRoomService;

class WaitingRoomIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketSectionRepository sectionRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private WaitingRoomService waitingRoomService;
    @Autowired private ReservationService reservationService;

    @Test
    void waitingRoomAdmitsLimitedUsersAndEnforcesAdmissionTokenOnReservation() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        var custRole = roleRepository.findByName(RoleName.CUSTOMER.name()).orElseThrow();
        var orgRole = roleRepository.findByName(RoleName.ORGANIZER.name()).orElseThrow();

        UserEntity org = userRepository.save(UserEntity.builder()
                .email("wr-org-" + sfx + "@test.com").passwordHash("x").fullName("Org")
                .status(UserStatus.ACTIVE).roles(Set.of(orgRole)).build());
        UserEntity u1 = userRepository.save(UserEntity.builder()
                .email("wr-u1-" + sfx + "@test.com").passwordHash("x").fullName("U1")
                .status(UserStatus.ACTIVE).roles(Set.of(custRole)).build());
        UserEntity u2 = userRepository.save(UserEntity.builder()
                .email("wr-u2-" + sfx + "@test.com").passwordHash("x").fullName("U2")
                .status(UserStatus.ACTIVE).roles(Set.of(custRole)).build());

        VenueEntity venue = venueRepository.save(VenueEntity.builder()
                .organizerId(org.getId()).name("WR Venue " + sfx).capacity(100).build());

        Instant now = Instant.now();
        EventEntity event = eventRepository.save(EventEntity.builder()
                .organizerId(org.getId())
                .venueId(venue.getId())
                .name("WR Concert " + sfx)
                .slug("wr-concert-" + sfx)
                .startTime(now.plus(3, ChronoUnit.DAYS))
                .endTime(now.plus(4, ChronoUnit.DAYS))
                .salesStartTime(now.minus(1, ChronoUnit.HOURS))
                .salesEndTime(now.plus(1, ChronoUnit.DAYS))
                .waitingRoomEnabled(true)
                .maxConcurrentUsers(1)
                .status(EventStatus.SALES_OPEN)
                .build());

        TicketSectionEntity section = sectionRepository.save(TicketSectionEntity.builder()
                .eventId(event.getId()).name("GA").price(500_000L).capacity(10).build());
        SeatEntity seat = seatRepository.save(SeatEntity.builder()
                .sectionId(section.getId()).seatCode("W01").rowLabel("W").seatNumber(1)
                .status(SeatStatus.AVAILABLE).build());

        var s1 = waitingRoomService.joinQueue(event.getId(), u1.getId());
        assertEquals("ADMITTED", s1.getStatus());
        assertNotNull(s1.getAdmissionToken());

        var s2 = waitingRoomService.joinQueue(event.getId(), u2.getId());
        assertEquals("WAITING", s2.getStatus());
        assertEquals(1L, s2.getQueuePosition());

        // Booking without token fails with Forbidden
        var reqNoToken = new ReservationDtos.CreateReservationRequest();
        reqNoToken.setSeatIds(List.of(seat.getId()));
        assertThrows(ForbiddenException.class,
                () -> reservationService.reserveSeats(u1.getId(), event.getId(), reqNoToken, null));

        // Booking with u1's valid token succeeds
        var res = reservationService.reserveSeats(u1.getId(), event.getId(), reqNoToken, s1.getAdmissionToken());
        assertNotNull(res.getReservationId());
    }
}
