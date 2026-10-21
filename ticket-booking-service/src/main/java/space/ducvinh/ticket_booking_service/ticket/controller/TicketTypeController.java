package space.ducvinh.ticket_booking_service.ticket.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.ticket.dto.TicketTypeDtos;
import space.ducvinh.ticket_booking_service.ticket.service.TicketTypeService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @GetMapping("/events/{id}/ticket-types")
    public ApiResponse<List<TicketTypeDtos.TicketTypeResponse>> listTicketTypes(@PathVariable UUID id) {
        return ApiResponse.ok(ticketTypeService.listByEvent(id));
    }

    @PostMapping("/organizer/events/{id}/ticket-types")
    public ResponseEntity<ApiResponse<TicketTypeDtos.TicketTypeResponse>> createTicketType(
            @PathVariable UUID id,
            @Valid @RequestBody TicketTypeDtos.CreateTicketTypeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Ticket type created", ticketTypeService.create(id, req)));
    }
}
