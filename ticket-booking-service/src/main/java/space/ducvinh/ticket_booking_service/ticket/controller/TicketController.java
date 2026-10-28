package space.ducvinh.ticket_booking_service.ticket.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.ticket.dto.TicketDtos;
import space.ducvinh.ticket_booking_service.ticket.service.TicketIssuanceService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketController {

    private final TicketIssuanceService ticketIssuanceService;

    @GetMapping("/users/me/tickets")
    public ApiResponse<PageResponse<TicketDtos.TicketResponse>> listMyTickets(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                ticketIssuanceService.listMyTickets(SecurityUtils.requireCurrentUserId(), pageable)));
    }

    @GetMapping("/tickets/{id}")
    public ApiResponse<TicketDtos.TicketResponse> getTicket(@PathVariable UUID id) {
        return ApiResponse.ok(ticketIssuanceService.getTicket(id));
    }

    @GetMapping("/tickets/{id}/qr")
    public ApiResponse<TicketDtos.TicketQrResponse> getTicketQr(@PathVariable UUID id) {
        return ApiResponse.ok(ticketIssuanceService.getTicketQr(id));
    }
}
