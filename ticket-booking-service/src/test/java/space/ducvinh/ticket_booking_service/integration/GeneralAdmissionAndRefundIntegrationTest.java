package space.ducvinh.ticket_booking_service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import space.ducvinh.ticket_booking_service.AbstractIntegrationTest;
import space.ducvinh.ticket_booking_service.admin.service.AdminService;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.order.dto.OrderDtos;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.order.service.OrderService;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;
import space.ducvinh.ticket_booking_service.payment.service.PaymentService;
import space.ducvinh.ticket_booking_service.reservation.dto.ReservationDtos;
import space.ducvinh.ticket_booking_service.reservation.service.ReservationService;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketTypeRepository;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.RoleName;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.RoleRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.repository.VenueRepository;

class GeneralAdmissionAndRefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AdminService adminService;

    @Test
    void generalAdmissionBooking_enforcesQuantityAndIssuesMultipleTicketsAndSupportsRefund() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        var custRole = roleRepository.findByName(RoleName.CUSTOMER.name()).orElseThrow();
        var orgRole = roleRepository.findByName(RoleName.ORGANIZER.name()).orElseThrow();
        var adminRole = roleRepository.findByName(RoleName.ADMIN.name()).orElseThrow();

        UserEntity org = userRepository.save(UserEntity.builder()
                .email("ga-org-" + sfx + "@test.com").passwordHash("x").fullName("Org")
                .status(UserStatus.ACTIVE).roles(Set.of(orgRole)).build());
        UserEntity cust = userRepository.save(UserEntity.builder()
                .email("ga-cust-" + sfx + "@test.com").passwordHash("x").fullName("Cust")
                .status(UserStatus.ACTIVE).roles(Set.of(custRole)).build());
        UserEntity admin = userRepository.save(UserEntity.builder()
                .email("ga-adm-" + sfx + "@test.com").passwordHash("x").fullName("Admin")
                .status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build());

        VenueEntity venue = venueRepository.save(VenueEntity.builder()
                .organizerId(org.getId()).name("Festival Park " + sfx).capacity(500).build());

        Instant now = Instant.now();
        EventEntity event = eventRepository.save(EventEntity.builder()
                .organizerId(org.getId()).venueId(venue.getId())
                .name("GA Festival " + sfx).slug("ga-fest-" + sfx)
                .startTime(now.plus(3, ChronoUnit.DAYS)).endTime(now.plus(4, ChronoUnit.DAYS))
                .salesStartTime(now.minus(1, ChronoUnit.HOURS)).salesEndTime(now.plus(1, ChronoUnit.DAYS))
                .maxSeatsPerReservation(5).status(EventStatus.SALES_OPEN).build());

        TicketTypeEntity ga = ticketTypeRepository.save(TicketTypeEntity.builder()
                .eventId(event.getId()).name("GA Standing").price(500_000L)
                .totalQuantity(3).soldQuantity(0).saleMode("GENERAL_ADMISSION").build());

        var req = new ReservationDtos.CreateReservationRequest();
        req.setTicketTypeId(ga.getId());
        req.setQuantity(2);
        var res = reservationService.reserveSeats(cust.getId(), event.getId(), req, null);
        assertEquals(1_000_000L, res.getTotalAmount());

        // Second request asking for 2 more exceeds remaining (1)
        var reqTooMany = new ReservationDtos.CreateReservationRequest();
        reqTooMany.setTicketTypeId(ga.getId());
        reqTooMany.setQuantity(2);
        assertThrows(ConflictException.class,
                () -> reservationService.reserveSeats(cust.getId(), event.getId(), reqTooMany, null));

        // Complete order & payment
        var orderReq = new OrderDtos.CreateOrderRequest();
        orderReq.setReservationId(res.getReservationId());
        var order = orderService.createOrder(cust.getId(), orderReq);

        var checkoutReq = new OrderDtos.CheckoutRequest();
        checkoutReq.setProvider(PaymentProvider.SANDBOX);
        var checkout = paymentService.checkout(cust.getId(), order.getId(), "ga-idem-" + sfx, checkoutReq);
        paymentService.completeSandboxPayment(checkout.getPaymentId(), null);

        var tickets = ticketRepository.findByOrderId(order.getId());
        assertEquals(2, tickets.size());
        assertEquals(2, ticketTypeRepository.findById(ga.getId()).orElseThrow().getSoldQuantity());

        // Admin processes refund
        var refunded = adminService.processRefund(admin.getId(), order.getId());
        assertEquals(OrderStatus.REFUNDED.name(), refunded.getStatus());
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        ticketRepository.findByOrderId(order.getId())
                .forEach(t -> assertEquals(TicketStatus.REFUNDED, t.getStatus()));
    }
}
