package space.ducvinh.ticket_booking_service.ticket.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.security.HmacSigningService;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.entity.OrderItemEntity;
import space.ducvinh.ticket_booking_service.order.repository.OrderItemRepository;
import space.ducvinh.ticket_booking_service.ticket.dto.TicketDtos;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;

@Service
@RequiredArgsConstructor
public class TicketIssuanceService {

    private final TicketRepository ticketRepository;
    private final OrderItemRepository orderItemRepository;
    private final SeatRepository seatRepository;
    private final TicketSectionRepository sectionRepository;
    private final EventRepository eventRepository;
    private final HmacSigningService hmacSigningService;
    private final OutboxService outboxService;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<TicketEntity> issueTicketsForPaidOrder(OrderEntity order, String userEmail) {
        List<TicketEntity> existing = ticketRepository.findByOrderId(order.getId());
        if (!existing.isEmpty()) {
            return existing;
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        List<TicketEntity> issued = new ArrayList<>();
        Instant now = Instant.now();

        for (OrderItemEntity item : items) {
            int qty = Math.max(1, item.getQuantity());
            for (int q = 0; q < qty; q++) {
                String ticketCode = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TicketEntity ticket = ticketRepository.save(TicketEntity.builder()
                        .orderId(order.getId())
                        .eventId(order.getEventId())
                        .seatId(item.getSeatId())
                        .ticketTypeId(item.getTicketTypeId())
                        .userId(order.getUserId())
                        .ticketCode(ticketCode)
                        .qrTokenHash("PENDING")
                        .status(TicketStatus.VALID)
                        .createdAt(now)
                        .build());

                String qrToken = buildSignedQrToken(ticket.getId(), order.getEventId(), item.getSeatId(), ticket.getCreatedAt());
                ticket.setQrTokenHash(JwtTokenProvider.sha256Hex(qrToken));
                issued.add(ticketRepository.save(ticket));
            }
        }

        outboxService.enqueue("ORDER", order.getId(), "TICKETS_ISSUED", Map.of(
                "orderId", order.getId().toString(),
                "orderCode", order.getOrderCode(),
                "userId", order.getUserId().toString(),
                "email", userEmail != null ? userEmail : "",
                "eventId", order.getEventId().toString(),
                "ticketCount", issued.size()
        ));
        return issued;
    }

    @Transactional(readOnly = true)
    public Page<TicketDtos.TicketResponse> listMyTickets(UUID userId, Pageable pageable) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TicketDtos.TicketResponse getTicket(UUID ticketId) {
        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        SecurityUtils.requireOwnerOrAdmin(ticket.getUserId());
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketDtos.TicketQrResponse getTicketQr(UUID ticketId) {
        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        SecurityUtils.requireOwnerOrAdmin(ticket.getUserId());

        String qrToken = buildSignedQrToken(
                ticket.getId(), ticket.getEventId(), ticket.getSeatId(), ticket.getCreatedAt());
        String base64Png = renderQrPngBase64(qrToken);

        return TicketDtos.TicketQrResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .qrToken(qrToken)
                .qrImageBase64(base64Png)
                .status(ticket.getStatus().name())
                .build();
    }

    public String buildSignedQrToken(UUID ticketId, UUID eventId, UUID seatId, Instant issuedAt) {
        String seatPart = seatId != null ? seatId.toString() : "GA";
        String payload = ticketId + "|" + eventId + "|" + seatPart + "|" + issuedAt.getEpochSecond();
        return hmacSigningService.signQrPayload(payload);
    }

    private String renderQrPngBase64(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 260, 260);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private TicketDtos.TicketResponse toResponse(TicketEntity t) {
        EventEntity event = eventRepository.findById(t.getEventId()).orElse(null);
        SeatEntity seat = t.getSeatId() != null ? seatRepository.findById(t.getSeatId()).orElse(null) : null;
        TicketSectionEntity sec = seat != null ? sectionRepository.findById(seat.getSectionId()).orElse(null) : null;
        return TicketDtos.TicketResponse.builder()
                .id(t.getId())
                .orderId(t.getOrderId())
                .eventId(t.getEventId())
                .eventName(event != null ? event.getName() : null)
                .seatId(t.getSeatId())
                .seatCode(seat != null ? seat.getSeatCode() : null)
                .sectionName(sec != null ? sec.getName() : null)
                .ticketCode(t.getTicketCode())
                .status(t.getStatus().name())
                .checkedInAt(t.getCheckedInAt())
                .issuedAt(t.getCreatedAt())
                .build();
    }
}
