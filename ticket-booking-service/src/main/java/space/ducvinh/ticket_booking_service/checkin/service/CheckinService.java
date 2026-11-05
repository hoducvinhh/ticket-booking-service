package space.ducvinh.ticket_booking_service.checkin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogService;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.security.AppUserPrincipal;
import space.ducvinh.ticket_booking_service.common.security.HmacSigningService;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.checkin.dto.CheckinDtos;
import space.ducvinh.ticket_booking_service.checkin.entity.CheckinLogEntity;
import space.ducvinh.ticket_booking_service.checkin.repository.CheckinLogRepository;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.entity.EventStaffEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.event.repository.EventStaffRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final TicketRepository ticketRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final EventRepository eventRepository;
    private final EventStaffRepository eventStaffRepository;
    private final HmacSigningService hmacSigningService;
    private final AuditLogService auditLogService;

    @Transactional
    public CheckinDtos.CheckinScanResponse scanQr(AppUserPrincipal staff, CheckinDtos.ScanQrRequest req) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(req.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        boolean isAdminOrOwner = staff.hasRole("ADMIN") || event.getOrganizerId().equals(staff.getUserId());
        if (!isAdminOrOwner && !eventStaffRepository.existsByEventIdAndStaffId(event.getId(), staff.getUserId())) {
            throw new ForbiddenException("Staff is not assigned to this event");
        }

        if (!hmacSigningService.verifyQrToken(req.getQrToken())) {
            throw new BadRequestException("Invalid or tampered QR signature");
        }

        String payload = hmacSigningService.extractQrPayload(req.getQrToken());
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw new BadRequestException("Malformed QR token");
        }

        UUID ticketId = UUID.fromString(parts[0]);
        UUID tokenEventId = UUID.fromString(parts[1]);
        if (!tokenEventId.equals(req.getEventId())) {
            throw new BusinessRuleViolationException("WRONG_EVENT", "Ticket does not belong to this event");
        }

        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (!ticket.getQrTokenHash().equals(JwtTokenProvider.sha256Hex(req.getQrToken()))) {
            throw new BadRequestException("QR token hash mismatch");
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED || ticket.getStatus() == TicketStatus.REFUNDED) {
            throw new BusinessRuleViolationException("TICKET_INVALID", "Ticket has been cancelled or refunded");
        }

        Instant now = Instant.now();
        int updated = ticketRepository.markCheckedInAtomically(ticketId, req.getEventId(), now);
        if (updated == 0) {
            throw new ConflictException("ALREADY_CHECKED_IN", "Ticket has already been checked in");
        }

        checkinLogRepository.save(CheckinLogEntity.builder()
                .ticketId(ticket.getId())
                .staffId(staff.getUserId())
                .eventId(req.getEventId())
                .checkedInAt(now)
                .deviceInfo(req.getDeviceInfo())
                .build());

        auditLogService.record(staff.getUserId(), "TICKET_CHECKED_IN", "TICKET", ticket.getId(),
                Map.of("eventId", req.getEventId().toString(), "ticketCode", ticket.getTicketCode()));

        return CheckinDtos.CheckinScanResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .eventId(req.getEventId())
                .seatId(ticket.getSeatId())
                .status(TicketStatus.CHECKED_IN.name())
                .checkedInAt(now)
                .message("Check-in successful")
                .build();
    }

    @Transactional(readOnly = true)
    public List<UUID> listAssignedEventIds(UUID staffId) {
        return eventStaffRepository.findByStaffId(staffId).stream()
                .map(EventStaffEntity::getEventId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CheckinDtos.CheckinLogResponse> getCheckinLogs(UUID eventId, Pageable pageable) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());
        return checkinLogRepository.findByEventIdOrderByCheckedInAtDesc(eventId, pageable)
                .map(l -> CheckinDtos.CheckinLogResponse.builder()
                        .id(l.getId())
                        .ticketId(l.getTicketId())
                        .staffId(l.getStaffId())
                        .eventId(l.getEventId())
                        .checkedInAt(l.getCheckedInAt())
                        .deviceInfo(l.getDeviceInfo())
                        .build());
    }

    @Transactional(readOnly = true)
    public CheckinDtos.CheckinStatisticsResponse getCheckinStatistics(UUID eventId) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        long totalIssued = ticketRepository.countByEventIdAndStatusIn(
                eventId, List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN));
        long checkedIn = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.CHECKED_IN);

        return CheckinDtos.CheckinStatisticsResponse.builder()
                .eventId(eventId)
                .totalIssuedTickets(totalIssued)
                .checkedInCount(checkedIn)
                .remainingCount(Math.max(0, totalIssued - checkedIn))
                .build();
    }
}
