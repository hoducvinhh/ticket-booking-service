package space.ducvinh.ticket_booking_service.common.seed;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.checkin.entity.CheckinLogEntity;
import space.ducvinh.ticket_booking_service.checkin.repository.CheckinLogRepository;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogEntity;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogRepository;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxEventEntity;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxEventRepository;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.entity.EventStaffEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.event.repository.EventStaffRepository;
import space.ducvinh.ticket_booking_service.notification.entity.NotificationEntity;
import space.ducvinh.ticket_booking_service.notification.repository.NotificationRepository;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderItemEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderItemRepository;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.payment.entity.PaymentEntity;
import space.ducvinh.ticket_booking_service.payment.entity.WebhookEventEntity;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;
import space.ducvinh.ticket_booking_service.payment.repository.PaymentRepository;
import space.ducvinh.ticket_booking_service.payment.repository.WebhookEventRepository;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationItemEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationItemRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketTypeRepository;
import space.ducvinh.ticket_booking_service.ticket.service.TicketIssuanceService;
import space.ducvinh.ticket_booking_service.user.entity.RoleEntity;
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
import space.ducvinh.ticket_booking_service.waitingroom.entity.WaitingRoomEntryEntity;
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;
import space.ducvinh.ticket_booking_service.waitingroom.repository.WaitingRoomEntryRepository;

/**
 * Seeds coherent, realistic demo data when the database has no users or events yet.
 * Skipped automatically under the {@code test} profile to keep integration tests isolated.
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "FlashTix@2026";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final EventStaffRepository eventStaffRepository;
    private final TicketSectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final TicketRepository ticketRepository;
    private final TicketIssuanceService ticketIssuanceService;
    private final CheckinLogRepository checkinLogRepository;
    private final WaitingRoomEntryRepository waitingRoomEntryRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;

    private int orderSeq = 1001;
    private int ticketSeq = 5001;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    public void seedIfEmpty() {
        if (userRepository.count() > 0 || eventRepository.count() > 0) {
            return;
        }

        log.info("Seeding realistic demo dataset for FlashTix...");
        Instant now = Instant.now();

        RoleEntity customerRole = requireRole(RoleName.CUSTOMER);
        RoleEntity organizerRole = requireRole(RoleName.ORGANIZER);
        RoleEntity staffRole = requireRole(RoleName.CHECKIN_STAFF);
        RoleEntity adminRole = requireRole(RoleName.ADMIN);

        String encodedPwd = passwordEncoder.encode(DEMO_PASSWORD);

        UserEntity admin = createUser("admin@flashtix.vn", "Hồ Đức Vinh (Quản trị viên)", "0901000001",
                UserStatus.ACTIVE, encodedPwd, adminRole, customerRole);
        UserEntity organizer1 = createUser("organizer@flashtix.vn", "Saigon Live Nation", "0902000002",
                UserStatus.ACTIVE, encodedPwd, organizerRole, customerRole);
        UserEntity organizer2 = createUser("techconf@flashtix.vn", "Cộng đồng TechSummit Việt Nam", "0902000003",
                UserStatus.ACTIVE, encodedPwd, organizerRole);
        UserEntity staff1 = createUser("staff@flashtix.vn", "Nguyễn Minh Tuấn (Trưởng cổng A)", "0903000004",
                UserStatus.ACTIVE, encodedPwd, staffRole);
        UserEntity staff2 = createUser("gateb@flashtix.vn", "Lê Thu Hà (Nhân viên cổng B)", "0903000005",
                UserStatus.ACTIVE, encodedPwd, staffRole);

        UserEntity buyer = createUser("buyer@flashtix.vn", "Nguyễn Hoàng An", "0911222333",
                UserStatus.ACTIVE, encodedPwd, customerRole);
        UserEntity linh = createUser("linh.tran@flashtix.vn", "Trần Khánh Linh", "0911222444",
                UserStatus.ACTIVE, encodedPwd, customerRole);
        UserEntity hoang = createUser("hoang.pham@flashtix.vn", "Phạm Minh Hoàng", "0911222555",
                UserStatus.ACTIVE, encodedPwd, customerRole);
        createUser("locked.user@flashtix.vn", "Tài khoản vi phạm (Đã khóa)", "0999000999",
                UserStatus.LOCKED, encodedPwd, customerRole);

        // Venues
        VenueEntity phuTho = createVenue(organizer1.getId(),
                "Nhà thi đấu Phú Thọ",
                "Số 1 Lý Thường Kiệt, Quận 11, TP. Hồ Chí Minh",
                "Nhà thi đấu đa năng quy mô lớn tại TP.HCM, chuyên tổ chức các đại nhạc hội, concert và giải đấu eSports.",
                5000);
        VenueEntity secc = createVenue(organizer2.getId(),
                "Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC)",
                "799 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh",
                "Tổ hợp hội nghị và triển lãm chuẩn quốc tế dành cho các sự kiện công nghệ và diễn đàn doanh nghiệp.",
                2500);
        VenueEntity myDinh = createVenue(organizer1.getId(),
                "Sân vận động Quốc gia Mỹ Đình",
                "Đường Lê Đức Thọ, Nam Từ Liêm, Hà Nội",
                "Sân vận động quốc gia sức chứa lớn phục vụ các trận cầu đỉnh cao và tour diễn ngoài trời.",
                40000);
        VenueEntity hoaBinh = createVenue(organizer1.getId(),
                "Nhà hát Hòa Bình",
                "240 Ba Tháng Hai, Quận 10, TP. Hồ Chí Minh",
                "Khán phòng nghệ thuật với hệ thống âm thanh chuẩn thính phòng, phù hợp cho hòa nhạc giao hưởng và sân khấu kịch.",
                2000);

        // Event 1: Flash Sale Music Concert (Waiting Room Enabled, Next 7 Days)
        EventEntity neonPulse = createEvent(
                organizer1.getId(), phuTho.getId(),
                "Đêm Nhạc Neon Pulse World Tour — TP.HCM",
                "neon-pulse-world-tour-hcmc",
                "Music",
                "Trải nghiệm đại nhạc hội điện tử và pop kéo dài 3 giờ với sân khấu 360 độ, dàn nhạc giao hưởng kết hợp "
                        + "và hệ thống vòng tay ánh sáng đồng bộ. Sự kiện áp dụng phòng chờ ảo để đảm bảo công bằng khi săn vé.",
                now.plus(5, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS),
                true, 250, 6, EventStatus.SALES_OPEN);

        List<SeatEntity> neonVip = createSectionWithSeats(
                neonPulse.getId(), "Khu VIP Sát Sân Khấu", 2_500_000L, "RESERVED", new String[]{"A", "B"}, 10);
        List<SeatEntity> neonCat1 = createSectionWithSeats(
                neonPulse.getId(), "Khu CAT 1 Khán Đài", 1_500_000L, "RESERVED", new String[]{"C", "D", "E"}, 10);
        List<SeatEntity> neonCat2 = createSectionWithSeats(
                neonPulse.getId(), "Khu CAT 2 Tầng Trên", 850_000L, "RESERVED", new String[]{"F", "G"}, 10);
        TicketTypeEntity neonGa = createTicketType(
                neonPulse.getId(), null, "Vé Đứng Early Bird", 650_000L, 200, "GENERAL_ADMISSION");

        blockSeats(List.of(neonCat2.get(18), neonCat2.get(19)));

        // Event 2: Tech Conference (No Waiting Room, Next 7 Days, supports Reserved + GA + active HELD seats)
        EventEntity cloudSummit = createEvent(
                organizer2.getId(), secc.getId(),
                "Hội Nghị Vietnam Cloud & AI Summit 2026",
                "vietnam-cloud-ai-summit-2026",
                "Tech",
                "Hội nghị kỹ thuật thường niên quy tụ hơn 40 phiên chia sẻ chuyên sâu về hệ thống phân tán, "
                        + "Kubernetes, vận hành LLM quy mô lớn và tối ưu kiến trúc PostgreSQL chịu tải cao.",
                now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS),
                now.minus(5, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS),
                false, 500, 6, EventStatus.SALES_OPEN);

        List<SeatEntity> summitExec = createSectionWithSeats(
                cloudSummit.getId(), "Khu Executive Lounge", 3_200_000L, "RESERVED", new String[]{"A", "B"}, 8);
        List<SeatEntity> summitStd = createSectionWithSeats(
                cloudSummit.getId(), "Khu Hội Thảo Tiêu Chuẩn", 1_200_000L, "RESERVED", new String[]{"C", "D", "E", "F"}, 10);
        TicketTypeEntity summitStudentGa = createTicketType(
                cloudSummit.getId(), null, "Vé Sinh Viên & Lập Trình Viên", 450_000L, 150, "GENERAL_ADMISSION");

        // Event 3: Sports Match (Later this month)
        EventEntity charityMatch = createEvent(
                organizer1.getId(), myDinh.getId(),
                "Trận Cầu Giao Hữu V-League All-Star 2026",
                "v-league-all-star-charity-2026",
                "Sports",
                "Quy tụ các danh thủ và ngôi sao V-League tại sân Mỹ Đình trong trận đấu giao hữu gây quỹ "
                        + "phát triển bóng đá học đường trên toàn quốc.",
                now.plus(14, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(3, ChronoUnit.DAYS), now.plus(13, ChronoUnit.DAYS),
                false, 1000, 6, EventStatus.SALES_OPEN);

        List<SeatEntity> matchVip = createSectionWithSeats(
                charityMatch.getId(), "Khán Đài VIP", 1_800_000L, "RESERVED", new String[]{"A", "B"}, 8);
        List<SeatEntity> matchGrandstand = createSectionWithSeats(
                charityMatch.getId(), "Khán Đài A", 600_000L, "RESERVED", new String[]{"C", "D", "E"}, 10);
        TicketTypeEntity matchGa = createTicketType(
                charityMatch.getId(), null, "Vé Cổ Động Viên Khán Đài Bắc", 350_000L, 300, "GENERAL_ADMISSION");

        // Event 4: Theater Symphony (PUBLISHED)
        EventEntity symphony = createEvent(
                organizer1.getId(), hoaBinh.getId(),
                "Đêm Nhạc Giao Hưởng Mùa Thu: Chopin & Mozart",
                "autumn-symphony-night-2026",
                "Theater",
                "Không gian hòa nhạc thính phòng ấm cúng dưới ánh nến do Dàn nhạc Giao hưởng Sài Gòn trình diễn cùng các nghệ sĩ độc tấu.",
                now.plus(21, ChronoUnit.DAYS), now.plus(21, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS),
                false, 300, 4, EventStatus.PUBLISHED);

        List<SeatEntity> symphonyOrch = createSectionWithSeats(
                symphony.getId(), "Hàng Ghế Vàng Orchestra", 2_200_000L, "RESERVED", new String[]{"A", "B"}, 10);
        createSectionWithSeats(
                symphony.getId(), "Ban Công Tầng 2", 950_000L, "RESERVED", new String[]{"C", "D"}, 12);

        // Event 5: Imminent Event (SALES_CLOSED, tomorrow — check-in active)
        EventEntity indieNight = createEvent(
                organizer1.getId(), hoaBinh.getId(),
                "Đêm Nhạc Acoustic Nửa Đêm — Phiên Bản Giới Hạn",
                "midnight-indie-acoustic-session",
                "Music",
                "Buổi biểu diễn acoustic thân mật đã bán hết vé với sự góp mặt của những giọng ca indie được yêu thích nhất.",
                now.plus(18, ChronoUnit.HOURS), now.plus(22, ChronoUnit.HOURS),
                now.minus(14, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS),
                false, 200, 4, EventStatus.SALES_CLOSED);

        List<SeatEntity> indieSeats = createSectionWithSeats(
                indieNight.getId(), "Khu Sân Khấu Gần", 900_000L, "RESERVED", new String[]{"A", "B"}, 10);
        blockSeats(List.of(indieSeats.get(19)));

        // Event 6: Past Completed Event (COMPLETED — populated analytics + check-in logs)
        EventEntity pitchDay = createEvent(
                organizer2.getId(), secc.getId(),
                "Ngày Hội Khởi Nghiệp TechCrunch Vietnam 2026",
                "techcrunch-vietnam-pitch-day-2026",
                "Tech",
                "Sự kiện gọi vốn thường niên quy tụ 15 startup nổi bật trình bày trực tiếp trước các quỹ đầu tư khu vực Đông Nam Á.",
                now.minus(10, ChronoUnit.DAYS), now.minus(9, ChronoUnit.DAYS),
                now.minus(30, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS),
                false, 300, 6, EventStatus.COMPLETED);

        List<SeatEntity> pitchSeats = createSectionWithSeats(
                pitchDay.getId(), "Hội Trường Chính", 1_000_000L, "RESERVED", new String[]{"A", "B"}, 10);

        // Assign check-in staff across events
        assignStaff(organizer1.getId(), List.of(neonPulse, charityMatch, indieNight), staff1.getId(), staff2.getId());
        assignStaff(organizer2.getId(), List.of(cloudSummit, pitchDay), staff1.getId(), staff2.getId());

        // Paid orders & signed QR tickets (Reserved + GA)
        // Event 1 (Neon Pulse)
        createPaidReservedOrder(buyer, neonPulse, List.of(neonVip.get(0), neonVip.get(1)),
                2_500_000L, PaymentProvider.PAYOS, 0, null);
        createPaidReservedOrder(linh, neonPulse, List.of(neonVip.get(2), neonVip.get(3), neonVip.get(4)),
                2_500_000L, PaymentProvider.VNPAY, 0, null);
        createPaidReservedOrder(hoang, neonPulse, List.of(neonCat1.get(0), neonCat1.get(1)),
                1_500_000L, PaymentProvider.PAYOS, 0, null);
        createPaidGaOrder(linh, neonPulse, neonGa, 2, PaymentProvider.SANDBOX);

        // Event 2 (Cloud & AI Summit)
        createPaidReservedOrder(buyer, cloudSummit, List.of(summitExec.get(0)),
                3_200_000L, PaymentProvider.VNPAY, 0, null);
        createPaidGaOrder(buyer, cloudSummit, summitStudentGa, 2, PaymentProvider.PAYOS);
        createPaidGaOrder(hoang, cloudSummit, summitStudentGa, 1, PaymentProvider.SANDBOX);
        // Active pending-payment order + live held seats for buyer (yellow HELD on seat map!)
        createPendingPaymentOrder(buyer, cloudSummit, List.of(summitStd.get(0), summitStd.get(1)),
                1_200_000L, now.plus(10, ChronoUnit.MINUTES));
        // One failed payment attempt on Event 2 so admin failure telemetry is non-zero
        createFailedPaymentOrder(hoang, cloudSummit, List.of(summitStd.get(5)), 1_200_000L);

        // Event 3 (Charity Match)
        createPaidReservedOrder(linh, charityMatch, List.of(matchVip.get(0), matchVip.get(1)),
                1_800_000L, PaymentProvider.PAYOS, 0, null);
        createPaidGaOrder(hoang, charityMatch, matchGa, 4, PaymentProvider.VNPAY);
        // Refunded order for Linh
        createRefundedOrder(linh, charityMatch, matchGrandstand.get(0), 600_000L);
        // Actionable REFUND_PENDING order for Hoang (late webhook scenario ready for Admin demo)
        createRefundPendingOrder(hoang, charityMatch, matchGrandstand.get(1), 600_000L);
        // Expired historical order for buyer
        createExpiredOrder(buyer, charityMatch, matchGrandstand.get(2), 600_000L);

        // Event 4 (Symphony)
        createPaidReservedOrder(linh, symphony, List.of(symphonyOrch.get(0), symphonyOrch.get(1)),
                2_200_000L, PaymentProvider.PAYOS, 0, null);

        // Event 5 (Midnight Indie — tomorrow, some early VIP check-in + valid passes)
        createPaidReservedOrder(buyer, indieNight, List.of(indieSeats.get(0), indieSeats.get(1)),
                900_000L, PaymentProvider.PAYOS, 0, null);
        createPaidReservedOrder(linh, indieNight, List.of(indieSeats.get(2), indieSeats.get(3), indieSeats.get(4)),
                900_000L, PaymentProvider.VNPAY, 2, staff1);
        createPaidReservedOrder(hoang, indieNight, List.of(indieSeats.get(5), indieSeats.get(6), indieSeats.get(7)),
                900_000L, PaymentProvider.SANDBOX, 1, staff2);

        // Event 6 (Completed TechCrunch — rich check-in logs and past revenue)
        createPaidReservedOrder(buyer, pitchDay, List.of(pitchSeats.get(0), pitchSeats.get(1)),
                1_000_000L, PaymentProvider.PAYOS, 2, staff1);
        createPaidReservedOrder(linh, pitchDay, List.of(pitchSeats.get(2), pitchSeats.get(3), pitchSeats.get(4), pitchSeats.get(5)),
                1_000_000L, PaymentProvider.VNPAY, 4, staff1);
        createPaidReservedOrder(hoang, pitchDay, List.of(pitchSeats.get(6), pitchSeats.get(7), pitchSeats.get(8), pitchSeats.get(9)),
                1_000_000L, PaymentProvider.PAYOS, 3, staff2);

        // Failed webhook sample for Admin observability
        webhookEventRepository.save(WebhookEventEntity.builder()
                .provider("PAYOS")
                .providerEventId("evt-bad-sig-demo-001")
                .payloadHash(JwtTokenProvider.sha256Hex("{\"orderCode\":\"ORD-UNKNOWN\",\"status\":\"PAID\"}"))
                .rawPayload("{\"orderCode\":\"ORD-UNKNOWN\",\"amount\":1500000,\"status\":\"PAID\"}")
                .status("FAILED")
                .errorMessage("Chữ ký HMAC webhook không hợp lệ")
                .receivedAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        // Waiting room historical/admitted entries on Event 1
        waitingRoomEntryRepository.save(WaitingRoomEntryEntity.builder()
                .eventId(neonPulse.getId())
                .userId(linh.getId())
                .status(WaitingRoomStatus.ADMITTED)
                .joinedAt(now.minus(12, ChronoUnit.MINUTES))
                .admittedAt(now.minus(8, ChronoUnit.MINUTES))
                .tokenExpiresAt(now.plus(5, ChronoUnit.MINUTES))
                .build());
        waitingRoomEntryRepository.save(WaitingRoomEntryEntity.builder()
                .eventId(neonPulse.getId())
                .userId(hoang.getId())
                .status(WaitingRoomStatus.EXPIRED)
                .joinedAt(now.minus(2, ChronoUnit.HOURS))
                .admittedAt(now.minus(115, ChronoUnit.MINUTES))
                .tokenExpiresAt(now.minus(105, ChronoUnit.MINUTES))
                .build());

        // Notifications for buyer, linh, hoang
        seedNotification(buyer.getId(), "TICKETS_ISSUED",
                "Đã phát hành vé: Đêm Nhạc Neon Pulse World Tour",
                "Hai vé Khu VIP Sát Sân Khấu (A01, A02) của bạn đã sẵn sàng kèm mã QR check-in.", false);
        seedNotification(buyer.getId(), "EVENT_REMINDER_24H",
                "Nhắc lịch: Đêm Nhạc Acoustic Nửa Đêm diễn ra vào ngày mai",
                "Cổng soát vé tại Nhà hát Hòa Bình sẽ mở sớm trước 60 phút. Bạn nhớ chuẩn bị sẵn mã QR nhé.", false);
        seedNotification(buyer.getId(), "RESERVATION_EXPIRING",
                "Hoàn tất thanh toán đơn Hội Nghị Vietnam Cloud & AI Summit 2026",
                "Ghế C01, C02 đang được giữ cho đơn hàng của bạn. Vui lòng thanh toán trước khi hết thời gian giữ chỗ.", false);
        seedNotification(buyer.getId(), "TICKETS_ISSUED",
                "Đã phát hành vé: Ngày Hội Khởi Nghiệp TechCrunch Vietnam 2026",
                "Cảm ơn bạn đã tham dự Ngày Hội Khởi Nghiệp TechCrunch Vietnam 2026.", true);

        seedNotification(linh.getId(), "PAYMENT_REFUNDED",
                "Hoàn tiền thành công: Trận Cầu Giao Hữu V-League All-Star",
                "Số tiền 600.000 đ đã được hoàn về phương thức thanh toán ban đầu của bạn.", false);
        seedNotification(hoang.getId(), "REFUND_PENDING",
                "Thanh toán ghi nhận sau khi hết hạn giữ chỗ",
                "Giao dịch cho đơn Trận Cầu Giao Hữu V-League All-Star được ghi nhận sau khi hết thời gian giữ ghế và đang chờ hoàn tiền.", false);

        // Audit logs & published outbox events
        seedAuditLog(organizer1.getId(), "EVENT_PUBLISHED", "EVENT", neonPulse.getId(),
                "{\"name\":\"Neon Pulse World Tour - Ho Chi Minh City\",\"waitingRoom\":true}");
        seedAuditLog(organizer2.getId(), "EVENT_PUBLISHED", "EVENT", cloudSummit.getId(),
                "{\"name\":\"Vietnam Cloud & AI Summit 2026\",\"waitingRoom\":false}");
        seedAuditLog(organizer1.getId(), "STAFF_ASSIGNED", "EVENT", indieNight.getId(),
                "{\"staffCount\":2}");
        seedAuditLog(admin.getId(), "ORDER_REFUNDED", "ORDER", null,
                "{\"amount\":600000,\"reason\":\"Customer requested cancellation\"}");

        outboxEventRepository.save(OutboxEventEntity.builder()
                .aggregateType("EVENT")
                .aggregateId(neonPulse.getId())
                .eventType("EVENT_PUBLISHED")
                .payload("{\"eventId\":\"" + neonPulse.getId() + "\",\"status\":\"SALES_OPEN\"}")
                .status("PUBLISHED")
                .publishedAt(now.minus(2, ChronoUnit.DAYS))
                .build());

        log.info("Demo seed completed: {} users, {} venues, {} events, {} orders, {} tickets.",
                userRepository.count(), venueRepository.count(), eventRepository.count(),
                orderRepository.count(), ticketRepository.count());
    }

    private RoleEntity requireRole(RoleName name) {
        return roleRepository.findByName(name.name())
                .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + name));
    }

    private UserEntity createUser(
            String email, String fullName, String phone, UserStatus status, String encodedPwd, RoleEntity... roles) {
        return userRepository.save(UserEntity.builder()
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .status(status)
                .passwordHash(encodedPwd)
                .roles(Set.of(roles))
                .build());
    }

    private VenueEntity createVenue(UUID organizerId, String name, String address, String description, int capacity) {
        return venueRepository.save(VenueEntity.builder()
                .organizerId(organizerId)
                .name(name)
                .address(address)
                .description(description)
                .capacity(capacity)
                .build());
    }

    private EventEntity createEvent(
            UUID organizerId, UUID venueId, String name, String slug, String category, String description,
            Instant startTime, Instant endTime, Instant salesStartTime, Instant salesEndTime,
            boolean waitingRoomEnabled, int maxConcurrentUsers, int maxSeatsPerReservation, EventStatus status) {
        return eventRepository.save(EventEntity.builder()
                .organizerId(organizerId)
                .venueId(venueId)
                .name(name)
                .slug(slug)
                .category(category)
                .description(description)
                .startTime(startTime)
                .endTime(endTime)
                .salesStartTime(salesStartTime)
                .salesEndTime(salesEndTime)
                .waitingRoomEnabled(waitingRoomEnabled)
                .maxConcurrentUsers(maxConcurrentUsers)
                .maxSeatsPerReservation(maxSeatsPerReservation)
                .status(status)
                .build());
    }

    private List<SeatEntity> createSectionWithSeats(
            UUID eventId, String name, long price, String type, String[] rows, int seatsPerRow) {
        TicketSectionEntity section = sectionRepository.save(TicketSectionEntity.builder()
                .eventId(eventId)
                .name(name)
                .price(price)
                .capacity(rows.length * seatsPerRow)
                .type(type)
                .build());

        List<SeatEntity> seats = new ArrayList<>();
        for (String row : rows) {
            for (int num = 1; num <= seatsPerRow; num++) {
                String code = row + String.format("%02d", num);
                seats.add(SeatEntity.builder()
                        .sectionId(section.getId())
                        .seatCode(code)
                        .rowLabel(row)
                        .seatNumber(num)
                        .status(SeatStatus.AVAILABLE)
                        .build());
            }
        }
        return seatRepository.saveAll(seats);
    }

    private TicketTypeEntity createTicketType(
            UUID eventId, UUID sectionId, String name, long price, int totalQuantity, String saleMode) {
        return ticketTypeRepository.save(TicketTypeEntity.builder()
                .eventId(eventId)
                .sectionId(sectionId)
                .name(name)
                .price(price)
                .totalQuantity(totalQuantity)
                .soldQuantity(0)
                .saleMode(saleMode)
                .build());
    }

    private void blockSeats(List<SeatEntity> seats) {
        for (SeatEntity s : seats) {
            s.setStatus(SeatStatus.BLOCKED);
        }
        seatRepository.saveAll(seats);
    }

    private void assignStaff(UUID organizerId, List<EventEntity> events, UUID... staffIds) {
        for (EventEntity ev : events) {
            for (UUID staffId : staffIds) {
                eventStaffRepository.save(EventStaffEntity.builder()
                        .eventId(ev.getId())
                        .staffId(staffId)
                        .assignedBy(organizerId)
                        .build());
            }
        }
    }

    private void createPaidReservedOrder(
            UserEntity user, EventEntity event, List<SeatEntity> seats, long unitPrice,
            PaymentProvider provider, int checkedInCount, UserEntity checkInStaff) {
        Instant now = Instant.now();
        long total = unitPrice * seats.size();

        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.CONFIRMED)
                .expiresAt(now.plusSeconds(600))
                .build());

        for (SeatEntity seat : seats) {
            reservationItemRepository.save(ReservationItemEntity.builder()
                    .reservationId(reservation.getId())
                    .seatId(seat.getId())
                    .quantity(1)
                    .unitPrice(unitPrice)
                    .build());
        }

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(total)
                .currency("VND")
                .status(OrderStatus.PAID)
                .build());

        for (SeatEntity seat : seats) {
            orderItemRepository.save(OrderItemEntity.builder()
                    .orderId(order.getId())
                    .seatId(seat.getId())
                    .unitPrice(unitPrice)
                    .quantity(1)
                    .subtotal(unitPrice)
                    .build());

            seat.setStatus(SeatStatus.SOLD);
            seatRepository.save(seat);
        }

        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(provider.name())
                .providerPaymentId("DEMO-" + order.getOrderCode())
                .idempotencyKey("seed-idem-" + order.getOrderCode())
                .amount(total)
                .status(PaymentStatus.SUCCESS)
                .build());

        recordProcessedWebhook(provider, payment, order);

        for (int i = 0; i < seats.size(); i++) {
            SeatEntity seat = seats.get(i);
            boolean checkIn = i < checkedInCount && checkInStaff != null;
            issueSignedTicket(order, event.getId(), user.getId(), seat.getId(), null, checkIn, checkInStaff);
        }
    }

    private void createPaidGaOrder(
            UserEntity user, EventEntity event, TicketTypeEntity gaType, int quantity, PaymentProvider provider) {
        Instant now = Instant.now();
        long total = gaType.getPrice() * quantity;
        gaType.setSoldQuantity(gaType.getSoldQuantity() + quantity);
        ticketTypeRepository.save(gaType);

        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.CONFIRMED)
                .expiresAt(now.plusSeconds(600))
                .build());

        reservationItemRepository.save(ReservationItemEntity.builder()
                .reservationId(reservation.getId())
                .ticketTypeId(gaType.getId())
                .quantity(quantity)
                .unitPrice(gaType.getPrice())
                .build());

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(total)
                .currency("VND")
                .status(OrderStatus.PAID)
                .build());

        orderItemRepository.save(OrderItemEntity.builder()
                .orderId(order.getId())
                .ticketTypeId(gaType.getId())
                .unitPrice(gaType.getPrice())
                .quantity(quantity)
                .subtotal(total)
                .build());

        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(provider.name())
                .providerPaymentId("DEMO-" + order.getOrderCode())
                .idempotencyKey("seed-idem-" + order.getOrderCode())
                .amount(total)
                .status(PaymentStatus.SUCCESS)
                .build());

        recordProcessedWebhook(provider, payment, order);

        for (int i = 0; i < quantity; i++) {
            issueSignedTicket(order, event.getId(), user.getId(), null, gaType.getId(), false, null);
        }
    }

    private void createPendingPaymentOrder(
            UserEntity user, EventEntity event, List<SeatEntity> seats, long unitPrice, Instant expiresAt) {
        long total = unitPrice * seats.size();

        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build());

        for (SeatEntity seat : seats) {
            reservationItemRepository.save(ReservationItemEntity.builder()
                    .reservationId(reservation.getId())
                    .seatId(seat.getId())
                    .quantity(1)
                    .unitPrice(unitPrice)
                    .build());

            seatClaimRepository.save(SeatClaimEntity.builder()
                    .seatId(seat.getId())
                    .reservationId(reservation.getId())
                    .userId(user.getId())
                    .expiresAt(expiresAt)
                    .build());
        }

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(total)
                .currency("VND")
                .status(OrderStatus.PENDING_PAYMENT)
                .build());

        for (SeatEntity seat : seats) {
            orderItemRepository.save(OrderItemEntity.builder()
                    .orderId(order.getId())
                    .seatId(seat.getId())
                    .unitPrice(unitPrice)
                    .quantity(1)
                    .subtotal(unitPrice)
                    .build());
        }
    }

    private void createFailedPaymentOrder(UserEntity user, EventEntity event, List<SeatEntity> seats, long unitPrice) {
        Instant now = Instant.now();
        long total = unitPrice * seats.size();
        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.EXPIRED)
                .expiresAt(now.minus(3, ChronoUnit.HOURS))
                .build());

        for (SeatEntity seat : seats) {
            reservationItemRepository.save(ReservationItemEntity.builder()
                    .reservationId(reservation.getId())
                    .seatId(seat.getId())
                    .quantity(1)
                    .unitPrice(unitPrice)
                    .build());
        }

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(total)
                .currency("VND")
                .status(OrderStatus.EXPIRED)
                .build());

        for (SeatEntity seat : seats) {
            orderItemRepository.save(OrderItemEntity.builder()
                    .orderId(order.getId())
                    .seatId(seat.getId())
                    .unitPrice(unitPrice)
                    .quantity(1)
                    .subtotal(unitPrice)
                    .build());
        }

        paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(PaymentProvider.VNPAY.name())
                .providerPaymentId("DEMO-FAIL-" + order.getOrderCode())
                .idempotencyKey("seed-fail-" + order.getOrderCode())
                .amount(total)
                .status(PaymentStatus.FAILED)
                .failureReason("Thẻ không đủ số dư hoặc bị từ chối trong môi trường thử nghiệm")
                .build());
    }

    private void createRefundPendingOrder(UserEntity user, EventEntity event, SeatEntity seat, long unitPrice) {
        Instant now = Instant.now();
        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.EXPIRED)
                .expiresAt(now.minus(45, ChronoUnit.MINUTES))
                .build());

        reservationItemRepository.save(ReservationItemEntity.builder()
                .reservationId(reservation.getId())
                .seatId(seat.getId())
                .quantity(1)
                .unitPrice(unitPrice)
                .build());

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(unitPrice)
                .currency("VND")
                .status(OrderStatus.REFUND_PENDING)
                .build());

        orderItemRepository.save(OrderItemEntity.builder()
                .orderId(order.getId())
                .seatId(seat.getId())
                .unitPrice(unitPrice)
                .quantity(1)
                .subtotal(unitPrice)
                .build());

        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(PaymentProvider.PAYOS.name())
                .providerPaymentId("DEMO-" + order.getOrderCode())
                .idempotencyKey("seed-idem-" + order.getOrderCode())
                .amount(unitPrice)
                .status(PaymentStatus.SUCCESS)
                .failureReason("Thanh toán ghi nhận sau khi hết hạn giữ chỗ; đang chờ hoàn tiền")
                .build());

        recordProcessedWebhook(PaymentProvider.PAYOS, payment, order);
    }

    private void createRefundedOrder(UserEntity user, EventEntity event, SeatEntity seat, long unitPrice) {
        Instant now = Instant.now();
        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.CONFIRMED)
                .expiresAt(now.minus(1, ChronoUnit.DAYS))
                .build());

        reservationItemRepository.save(ReservationItemEntity.builder()
                .reservationId(reservation.getId())
                .seatId(seat.getId())
                .quantity(1)
                .unitPrice(unitPrice)
                .build());

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(unitPrice)
                .currency("VND")
                .status(OrderStatus.REFUNDED)
                .build());

        orderItemRepository.save(OrderItemEntity.builder()
                .orderId(order.getId())
                .seatId(seat.getId())
                .unitPrice(unitPrice)
                .quantity(1)
                .subtotal(unitPrice)
                .build());

        paymentRepository.save(PaymentEntity.builder()
                .orderId(order.getId())
                .provider(PaymentProvider.SANDBOX.name())
                .providerPaymentId("DEMO-" + order.getOrderCode())
                .idempotencyKey("seed-idem-" + order.getOrderCode())
                .amount(unitPrice)
                .status(PaymentStatus.REFUNDED)
                .build());

        TicketEntity ticket = ticketRepository.saveAndFlush(TicketEntity.builder()
                .orderId(order.getId())
                .eventId(event.getId())
                .seatId(seat.getId())
                .userId(user.getId())
                .ticketCode(nextTicketCode())
                .qrTokenHash("PENDING")
                .status(TicketStatus.REFUNDED)
                .build());
        String qrToken = ticketIssuanceService.buildSignedQrToken(
                ticket.getId(), event.getId(), seat.getId(), ticket.getCreatedAt());
        ticket.setQrTokenHash(JwtTokenProvider.sha256Hex(qrToken));
        ticketRepository.save(ticket);
    }

    private void createExpiredOrder(UserEntity user, EventEntity event, SeatEntity seat, long unitPrice) {
        Instant now = Instant.now();
        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(user.getId())
                .eventId(event.getId())
                .status(ReservationStatus.EXPIRED)
                .expiresAt(now.minus(4, ChronoUnit.HOURS))
                .build());

        reservationItemRepository.save(ReservationItemEntity.builder()
                .reservationId(reservation.getId())
                .seatId(seat.getId())
                .quantity(1)
                .unitPrice(unitPrice)
                .build());

        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .userId(user.getId())
                .reservationId(reservation.getId())
                .eventId(event.getId())
                .orderCode(nextOrderCode())
                .totalAmount(unitPrice)
                .currency("VND")
                .status(OrderStatus.EXPIRED)
                .build());

        orderItemRepository.save(OrderItemEntity.builder()
                .orderId(order.getId())
                .seatId(seat.getId())
                .unitPrice(unitPrice)
                .quantity(1)
                .subtotal(unitPrice)
                .build());
    }

    private void issueSignedTicket(
            OrderEntity order, UUID eventId, UUID userId, UUID seatId, UUID ticketTypeId,
            boolean checkedIn, UserEntity checkInStaff) {
        TicketEntity ticket = ticketRepository.saveAndFlush(TicketEntity.builder()
                .orderId(order.getId())
                .eventId(eventId)
                .seatId(seatId)
                .ticketTypeId(ticketTypeId)
                .userId(userId)
                .ticketCode(nextTicketCode())
                .qrTokenHash("PENDING")
                .status(checkedIn ? TicketStatus.CHECKED_IN : TicketStatus.VALID)
                .checkedInAt(checkedIn ? Instant.now().minus(2, ChronoUnit.HOURS) : null)
                .build());

        String qrToken = ticketIssuanceService.buildSignedQrToken(
                ticket.getId(), eventId, seatId, ticket.getCreatedAt());
        ticket.setQrTokenHash(JwtTokenProvider.sha256Hex(qrToken));
        ticketRepository.save(ticket);

        if (checkedIn && checkInStaff != null) {
            checkinLogRepository.save(CheckinLogEntity.builder()
                    .ticketId(ticket.getId())
                    .staffId(checkInStaff.getId())
                    .eventId(eventId)
                    .checkedInAt(ticket.getCheckedInAt())
                    .deviceInfo("Máy quét cổng số 01 (Zebra TC21)")
                    .build());
        }
    }

    private void recordProcessedWebhook(PaymentProvider provider, PaymentEntity payment, OrderEntity order) {
        String raw = "{\"orderCode\":\"" + order.getOrderCode() + "\",\"amount\":" + order.getTotalAmount()
                + ",\"status\":\"PAID\"}";
        webhookEventRepository.save(WebhookEventEntity.builder()
                .provider(provider.name())
                .providerEventId("evt-seed-" + order.getOrderCode())
                .payloadHash(JwtTokenProvider.sha256Hex(raw))
                .rawPayload(raw)
                .status("PROCESSED")
                .processedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build());
    }

    private void seedNotification(UUID userId, String type, String title, String content, boolean read) {
        notificationRepository.save(NotificationEntity.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .channel("IN_APP")
                .read(read)
                .build());
    }

    private void seedAuditLog(UUID actorId, String action, String resourceType, UUID resourceId, String metadata) {
        auditLogRepository.save(AuditLogEntity.builder()
                .actorId(actorId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .metadata(metadata)
                .build());
    }

    private String nextOrderCode() {
        return "ORD-2026-" + (orderSeq++);
    }

    private String nextTicketCode() {
        return "TKT-FT-" + (ticketSeq++);
    }
}
