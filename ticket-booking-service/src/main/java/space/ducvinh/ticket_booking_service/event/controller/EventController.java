package space.ducvinh.ticket_booking_service.event.controller;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.dto.EventDtos;
import space.ducvinh.ticket_booking_service.event.service.EventService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/events")
    public ApiResponse<PageResponse<EventDtos.EventResponse>> listEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @PageableDefault(size = 12) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                eventService.listPublicEvents(category, search, fromTime, toTime, pageable)));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventDtos.EventResponse> getEvent(@PathVariable UUID id) {
        return ApiResponse.ok(eventService.getEvent(id));
    }

    @GetMapping("/organizer/events")
    public ApiResponse<PageResponse<EventDtos.EventResponse>> listMyEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                eventService.listOrganizerEvents(SecurityUtils.requireCurrentUserId(), pageable)));
    }

    @PostMapping("/organizer/events")
    public ResponseEntity<ApiResponse<EventDtos.EventResponse>> createEvent(
            @Valid @RequestBody EventDtos.CreateEventRequest req) {
        EventDtos.EventResponse created = eventService.createEvent(SecurityUtils.requireCurrentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Event created", created));
    }

    @PatchMapping("/organizer/events/{id}")
    public ApiResponse<EventDtos.EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody EventDtos.UpdateEventRequest req) {
        return ApiResponse.ok("Event updated", eventService.updateEvent(id, req));
    }

    @DeleteMapping("/organizer/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/organizer/events/{id}/publish")
    public ApiResponse<EventDtos.EventResponse> publishEvent(@PathVariable UUID id) {
        return ApiResponse.ok("Event published", eventService.publishEvent(id));
    }

    @PostMapping("/organizer/events/{id}/hide")
    public ApiResponse<EventDtos.EventResponse> hideEvent(@PathVariable UUID id) {
        return ApiResponse.ok("Event hidden", eventService.hideEvent(id));
    }

    @PostMapping("/organizer/events/{id}/cancel")
    public ApiResponse<EventDtos.EventResponse> cancelEvent(@PathVariable UUID id) {
        return ApiResponse.ok("Event cancelled", eventService.cancelEvent(id));
    }

    @PostMapping("/organizer/events/{id}/banner")
    public ApiResponse<EventDtos.EventResponse> uploadBanner(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Banner uploaded", eventService.uploadBanner(id, file));
    }

    @PostMapping("/organizer/events/{id}/staff")
    public ApiResponse<Void> assignStaff(
            @PathVariable UUID id,
            @Valid @RequestBody EventDtos.AssignStaffRequest req) {
        eventService.assignStaff(id, req.getStaffId());
        return ApiResponse.okMessage("Staff assigned to event");
    }
}
