package space.ducvinh.ticket_booking_service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import space.ducvinh.ticket_booking_service.AbstractIntegrationTest;
import space.ducvinh.ticket_booking_service.checkin.dto.CheckinDtos;
import space.ducvinh.ticket_booking_service.checkin.repository.CheckinLogRepository;
import space.ducvinh.ticket_booking_service.checkin.service.CheckinService;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.security.AppUserPrincipal;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.entity.EventStaffEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.event.repository.EventStaffRepository;
import space.ducvinh.ticket_booking_service.order.dto.OrderDtos;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.order.service.OrderService;
import space.ducvinh.ticket_booking_service.payment.dto.PaymentDtos;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;
import space.ducvinh.ticket_booking_service.payment.service.PaymentGatewayAdapter;
import space.ducvinh.ticket_booking_service.payment.service.PaymentService;
import space.ducvinh.ticket_booking_service.reservation.dto.ReservationDtos;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.reservation.service.ReservationExpirationService;
import space.ducvinh.ticket_booking_service.reservation.service.ReservationService;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.ticket.service.TicketIssuanceService;
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

class BookingConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventStaffRepository eventStaffRepository;
    @Autowired private TicketSectionRepository sectionRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationExpirationService expirationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private SeatClaimRepository seatClaimRepository;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentGatewayAdapter gatewayAdapter;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketIssuanceService ticketIssuanceService;
    @Autowired private CheckinService checkinService;
    @Autowired private CheckinLogRepository checkinLogRepository;

    private UserEntity organizer;
    private UserEntity staffUser;
    private List<UserEntity> customers;
    private EventEntity event;
    private List<SeatEntity> seats;

    @BeforeEach
    void setupFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customerRole = roleRepository.findByName(RoleName.CUSTOMER.name()).orElseThrow();
        var organizerRole = roleRepository.findByName(RoleName.ORGANIZER.name()).orElseThrow();
        var staffRole = roleRepository.findByName(RoleName.CHECKIN_STAFF.name()).orElseThrow();

        organizer = userRepository.save(UserEntity.builder()
                .email("org-" + suffix + "@test.com")
                .passwordHash("hash")
                .fullName("Organizer " + suffix)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(organizerRole))
                .build());

        staffUser = userRepository.save(UserEntity.builder()
                .email("staff-" + suffix + "@test.com")
                .passwordHash("hash")
                .fullName("Staff " + suffix)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(staffRole))
                .build());

        customers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            customers.add(userRepository.save(UserEntity.builder()
                    .email("cust-" + i + "-" + suffix + "@test.com")
                    .passwordHash("hash")
                    .fullName("Customer " + i)
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(customerRole))
                    .build()));
        }

        VenueEntity venue = venueRepository.save(VenueEntity.builder()
                .organizerId(organizer.getId())
                .name("Arena " + suffix)
                .capacity(1000)
                .build());

        Instant now = Instant.now();
        event = eventRepository.save(EventEntity.builder()
                .organizerId(organizer.getId())
                .venueId(venue.getId())
                .name("Flash Sale Concert " + suffix)
                .slug("flash-sale-" + suffix)
                .startTime(now.plus(5, ChronoUnit.DAYS))
                .endTime(now.plus(6, ChronoUnit.DAYS))
                .salesStartTime(now.minus(1, ChronoUnit.HOURS))
                .salesEndTime(now.plus(2, ChronoUnit.DAYS))
                .waitingRoomEnabled(false)
                .maxSeatsPerReservation(6)
                .status(EventStatus.SALES_OPEN)
                .build());

        eventStaffRepository.save(EventStaffEntity.builder()
                .eventId(event.getId())
                .staffId(staffUser.getId())
                .assignedBy(organizer.getId())
                .build());

        TicketSectionEntity section = sectionRepository.save(TicketSectionEntity.builder()
                .eventId(event.getId())
                .name("VIP")
                .price(1_500_000L)
                .capacity(10)
                .build());

        seats = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            seats.add(seatRepository.save(SeatEntity.builder()
                    .sectionId(section.getId())
                    .seatCode("A0" + i)
                    .rowLabel("A")
                    .seatNumber(i)
                    .status(SeatStatus.AVAILABLE)
                    .build()));
        }
    }

    @Test
    void test1_doubleBooking_100ConcurrentRequests_onlyOneSucceeds() throws Exception {
        UUID targetSeatId = seats.get(0).getId();
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID userId = customers.get(i % customers.size()).getId();
            futures.add(pool.submit((Callable<Void>) () -> {
                startGate.await();
                var req = new ReservationDtos.CreateReservationRequest();
                req.setSeatIds(List.of(targetSeatId));
                try {
                    reservationService.reserveSeats(userId, event.getId(), req, null);
                    successCount.incrementAndGet();
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 request must win the seat");
        assertEquals(99, conflictCount.get(), "All 99 losing requests must receive Conflict");
        assertTrue(seatClaimRepository.findById(targetSeatId).isPresent());
    }

    @Test
    void test2_multipleSeatsOverlapping_noDeadlock() throws Exception {
        UUID seatA = seats.get(1).getId();
        UUID seatB = seats.get(2).getId();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID userId = customers.get(i % customers.size()).getId();
            // Half send [seatA, seatB], half send [seatB, seatA]
            List<UUID> requested = (i % 2 == 0) ? List.of(seatA, seatB) : List.of(seatB, seatA);
            futures.add(pool.submit(() -> {
                startGate.await();
                var req = new ReservationDtos.CreateReservationRequest();
                req.setSeatIds(requested);
                try {
                    reservationService.reserveSeats(userId, event.getId(), req, null);
                    successCount.incrementAndGet();
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(threads - 1, conflictCount.get());
    }

    @Test
    void test3_concurrentWebhooksAndIdempotentCheckout_noDuplicateTickets() throws Exception {
        UUID userId = customers.get(0).getId();
        UUID seatId = seats.get(3).getId();

        var resReq = new ReservationDtos.CreateReservationRequest();
        resReq.setSeatIds(List.of(seatId));
        var res = reservationService.reserveSeats(userId, event.getId(), resReq, null);

        var orderReq = new OrderDtos.CreateOrderRequest();
        orderReq.setReservationId(res.getReservationId());
        var order = orderService.createOrder(userId, orderReq);

        // Checkout idempotency check
        var checkoutReq = new OrderDtos.CheckoutRequest();
        checkoutReq.setProvider(PaymentProvider.PAYOS);
        var c1 = paymentService.checkout(userId, order.getId(), "idem-key-1", checkoutReq);
        var c2 = paymentService.checkout(userId, order.getId(), "idem-key-1", checkoutReq);
        assertEquals(c1.getPaymentId(), c2.getPaymentId());

        var differentReq = new OrderDtos.CheckoutRequest();
        differentReq.setProvider(PaymentProvider.VNPAY);
        assertThrows(ConflictException.class,
                () -> paymentService.checkout(userId, order.getId(), "idem-key-1", differentReq));

        // 10 concurrent identical webhooks
        String sig = gatewayAdapter.signPayOsWebhook(order.getOrderCode(), order.getTotalAmount(), "PAID");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                paymentService.handlePayOsWebhook(new PaymentDtos.PayOsWebhookRequest(
                        "evt-same-123", c1.getPaymentId().toString(), order.getOrderCode(),
                        order.getTotalAmount(), "PAID", sig));
                return null;
            }));
        }
        startGate.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        var updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, updatedOrder.getStatus());
        List<TicketEntity> tickets = ticketRepository.findByOrderId(order.getId());
        assertEquals(1, tickets.size(), "Must issue exactly 1 ticket despite 10 concurrent webhooks");
    }

    @Test
    void test4_reservationExpiration_releasesClaimForNextUser() {
        UUID seatId = seats.get(4).getId();
        var req = new ReservationDtos.CreateReservationRequest();
        req.setSeatIds(List.of(seatId));

        var res1 = reservationService.reserveSeats(customers.get(0).getId(), event.getId(), req, null);

        // Force expiration timestamp into the past
        var entity = reservationRepository.findById(res1.getReservationId()).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(10));
        reservationRepository.saveAndFlush(entity);

        boolean expired = expirationService.expireReservationIfStillActive(res1.getReservationId());
        assertTrue(expired);
        assertEquals(ReservationStatus.EXPIRED,
                reservationRepository.findById(res1.getReservationId()).orElseThrow().getStatus());
        assertTrue(seatClaimRepository.findById(seatId).isEmpty());

        // User B can now reserve the same seat
        var res2 = reservationService.reserveSeats(customers.get(1).getId(), event.getId(), req, null);
        assertNotNull(res2.getReservationId());
    }

    @Test
    void test5_concurrentCheckin_onlyOneStaffSucceeds() throws Exception {
        UUID userId = customers.get(2).getId();
        UUID seatId = seats.get(0).getId();

        var resReq = new ReservationDtos.CreateReservationRequest();
        resReq.setSeatIds(List.of(seatId));
        var res = reservationService.reserveSeats(userId, event.getId(), resReq, null);

        var orderReq = new OrderDtos.CreateOrderRequest();
        orderReq.setReservationId(res.getReservationId());
        var order = orderService.createOrder(userId, orderReq);

        var checkoutReq = new OrderDtos.CheckoutRequest();
        checkoutReq.setProvider(PaymentProvider.SANDBOX);
        var checkout = paymentService.checkout(userId, order.getId(), "idem-checkin-1", checkoutReq);
        paymentService.completeSandboxPayment(checkout.getPaymentId(), null);

        TicketEntity ticket = ticketRepository.findByOrderId(order.getId()).get(0);
        String qrToken = ticketIssuanceService.buildSignedQrToken(
                ticket.getId(), ticket.getEventId(), ticket.getSeatId(), ticket.getCreatedAt());

        AppUserPrincipal staffPrincipal = new AppUserPrincipal(
                staffUser.getId(), staffUser.getEmail(), "", "ACTIVE", Set.of("CHECKIN_STAFF"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger conflict = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                var scanReq = new CheckinDtos.ScanQrRequest();
                scanReq.setEventId(event.getId());
                scanReq.setQrToken(qrToken);
                try {
                    checkinService.scanQr(staffPrincipal, scanReq);
                    ok.incrementAndGet();
                } catch (ConflictException ex) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        gate.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, ok.get());
        assertEquals(1, conflict.get());
        assertEquals(1, checkinLogRepository.countByEventId(event.getId()));
    }

    @Test
    void test6_paymentSuccessAfterReservationExpired_setsRefundPendingAndIssuesNoTickets() {
        UUID userId = customers.get(3).getId();
        UUID seatId = seats.get(1).getId();

        var resReq = new ReservationDtos.CreateReservationRequest();
        resReq.setSeatIds(List.of(seatId));
        var res = reservationService.reserveSeats(userId, event.getId(), resReq, null);

        var orderReq = new OrderDtos.CreateOrderRequest();
        orderReq.setReservationId(res.getReservationId());
        var order = orderService.createOrder(userId, orderReq);

        var checkoutReq = new OrderDtos.CheckoutRequest();
        checkoutReq.setProvider(PaymentProvider.PAYOS);
        var checkout = paymentService.checkout(userId, order.getId(), "idem-late-1", checkoutReq);

        // Expire reservation before webhook arrives
        var resEntity = reservationRepository.findById(res.getReservationId()).orElseThrow();
        resEntity.setExpiresAt(Instant.now().minusSeconds(60));
        reservationRepository.saveAndFlush(resEntity);
        expirationService.expireReservationIfStillActive(res.getReservationId());

        String sig = gatewayAdapter.signPayOsWebhook(order.getOrderCode(), order.getTotalAmount(), "PAID");
        paymentService.handlePayOsWebhook(new PaymentDtos.PayOsWebhookRequest(
                "evt-late-1", checkout.getPaymentId().toString(), order.getOrderCode(),
                order.getTotalAmount(), "PAID", sig));

        var updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.REFUND_PENDING, updatedOrder.getStatus());
        assertTrue(ticketRepository.findByOrderId(order.getId()).isEmpty());
    }
}
