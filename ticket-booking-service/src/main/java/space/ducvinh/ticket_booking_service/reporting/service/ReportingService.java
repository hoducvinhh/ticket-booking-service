package space.ducvinh.ticket_booking_service.reporting.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.checkin.repository.CheckinLogRepository;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentStatus;
import space.ducvinh.ticket_booking_service.payment.repository.PaymentRepository;
import space.ducvinh.ticket_booking_service.payment.repository.WebhookEventRepository;
import space.ducvinh.ticket_booking_service.reporting.dto.ReportingDtos;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.waitingroom.enums.WaitingRoomStatus;
import space.ducvinh.ticket_booking_service.waitingroom.repository.WaitingRoomEntryRepository;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final UserRepository userRepository;
    private final WaitingRoomEntryRepository waitingRoomEntryRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public ReportingDtos.OrganizerEventStatsResponse getOrganizerEventStats(UUID eventId) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        long totalSeats = seatRepository.countByEventId(eventId);
        long sold = ticketRepository.countByEventIdAndStatusIn(
                eventId, List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN));
        long held = seatClaimRepository.countActiveClaimsByEventId(eventId, Instant.now());
        long available = Math.max(0L, totalSeats - sold - held);
        long totalOrders = orderRepository.countByEventId(eventId);
        long paidOrders = orderRepository.findByEventIdAndStatus(eventId, OrderStatus.PAID).size();
        long revenue = orderRepository.sumPaidRevenueByEventId(eventId);
        double successRate = totalOrders == 0 ? 0.0 : (paidOrders * 100.0) / totalOrders;
        long checkins = checkinLogRepository.countByEventId(eventId);

        return ReportingDtos.OrganizerEventStatsResponse.builder()
                .eventId(eventId)
                .totalTickets(totalSeats)
                .soldTickets(sold)
                .heldTickets(held)
                .availableTickets(available)
                .totalOrders(totalOrders)
                .totalRevenue(revenue)
                .paymentSuccessRate(Math.round(successRate * 100.0) / 100.0)
                .checkinCount(checkins)
                .build();
    }

    @Transactional(readOnly = true)
    public ReportingDtos.AdminOverviewStatsResponse getAdminOverview() {
        return ReportingDtos.AdminOverviewStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalEvents(eventRepository.count())
                .totalOrders(orderRepository.count())
                .totalPayments(paymentRepository.count())
                .totalRevenue(orderRepository.sumTotalPaidRevenue())
                .failedPayments(paymentRepository.countByStatus(PaymentStatus.FAILED))
                .failedWebhooks(webhookEventRepository.countByStatus("FAILED"))
                .waitingRoomQueueBacklog(waitingRoomEntryRepository.countByStatus(WaitingRoomStatus.WAITING))
                .reservationExpirationBacklog(
                        reservationRepository.countByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, Instant.now()))
                .build();
    }
}
