package space.ducvinh.ticket_booking_service.venue.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.venue.dto.VenueDtos;
import space.ducvinh.ticket_booking_service.venue.service.VenueService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping("/venues")
    public ApiResponse<PageResponse<VenueDtos.VenueResponse>> listVenues(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(venueService.listVenues(pageable)));
    }

    @GetMapping("/venues/{id}")
    public ApiResponse<VenueDtos.VenueResponse> getVenue(@PathVariable UUID id) {
        return ApiResponse.ok(venueService.getVenue(id));
    }

    @PostMapping("/organizer/venues")
    public ResponseEntity<ApiResponse<VenueDtos.VenueResponse>> createVenue(
            @Valid @RequestBody VenueDtos.CreateVenueRequest req) {
        VenueDtos.VenueResponse created = venueService.createVenue(SecurityUtils.requireCurrentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Venue created", created));
    }

    @PostMapping("/organizer/venues/{id}/sections")
    public ResponseEntity<ApiResponse<VenueDtos.SectionResponse>> createSection(
            @PathVariable UUID id,
            @Valid @RequestBody VenueDtos.CreateSectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Section created", venueService.createSection(id, req)));
    }

    @PostMapping("/organizer/sections/{id}/seats")
    public ResponseEntity<ApiResponse<List<VenueDtos.SeatResponse>>> createSeats(
            @PathVariable UUID id,
            @Valid @RequestBody VenueDtos.CreateSeatsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Seats created", venueService.createSeats(id, req)));
    }

    @PatchMapping("/organizer/seats/{id}")
    public ApiResponse<VenueDtos.SeatResponse> updateSeat(
            @PathVariable UUID id,
            @Valid @RequestBody VenueDtos.UpdateSeatStatusRequest req) {
        return ApiResponse.ok("Seat updated", venueService.updateSeatStatus(id, req));
    }
}
