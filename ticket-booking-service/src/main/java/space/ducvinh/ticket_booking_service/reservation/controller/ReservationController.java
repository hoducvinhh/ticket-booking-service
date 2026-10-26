package space.ducvinh.ticket_booking_service.reservation.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.reservation.dto.ReservationDtos;
import space.ducvinh.ticket_booking_service.reservation.service.ReservationService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/events/{id}/reservations")
    public ResponseEntity<ApiResponse<ReservationDtos.ReservationResponse>> reserveSeats(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admission-Token", required = false) String admissionToken,
            @Valid @RequestBody ReservationDtos.CreateReservationRequest req) {
        ReservationDtos.ReservationResponse res = reservationService.reserveSeats(
                SecurityUtils.requireCurrentUserId(), id, req, admissionToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Seats reserved", res));
    }

    @GetMapping("/reservations/{id}")
    public ApiResponse<ReservationDtos.ReservationResponse> getReservation(@PathVariable UUID id) {
        return ApiResponse.ok(reservationService.getReservation(id));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/reservations")
    public ApiResponse<PageResponse<ReservationDtos.ReservationResponse>> listMyReservations(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                reservationService.listMyReservations(SecurityUtils.requireCurrentUserId(), pageable)));
    }
}
