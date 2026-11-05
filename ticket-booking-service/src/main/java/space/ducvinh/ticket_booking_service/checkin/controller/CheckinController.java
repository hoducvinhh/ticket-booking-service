package space.ducvinh.ticket_booking_service.checkin.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.checkin.dto.CheckinDtos;
import space.ducvinh.ticket_booking_service.checkin.service.CheckinService;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping("/check-in/scan")
    public ApiResponse<CheckinDtos.CheckinScanResponse> scan(@Valid @RequestBody CheckinDtos.ScanQrRequest req) {
        return ApiResponse.ok(checkinService.scanQr(SecurityUtils.requireCurrentPrincipal(), req));
    }

    @GetMapping("/check-in/my-events")
    public ApiResponse<List<UUID>> listMyAssignedEvents() {
        return ApiResponse.ok(checkinService.listAssignedEventIds(SecurityUtils.requireCurrentUserId()));
    }

    @GetMapping("/organizer/events/{id}/check-ins")
    public ApiResponse<PageResponse<CheckinDtos.CheckinLogResponse>> getCheckinLogs(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(checkinService.getCheckinLogs(id, pageable)));
    }

    @GetMapping("/organizer/events/{id}/check-in-statistics")
    public ApiResponse<CheckinDtos.CheckinStatisticsResponse> getStatistics(@PathVariable UUID id) {
        return ApiResponse.ok(checkinService.getCheckinStatistics(id));
    }
}
