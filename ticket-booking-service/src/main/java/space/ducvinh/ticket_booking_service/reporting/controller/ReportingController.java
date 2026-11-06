package space.ducvinh.ticket_booking_service.reporting.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.reporting.dto.ReportingDtos;
import space.ducvinh.ticket_booking_service.reporting.service.ReportingService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/organizer/events/{id}/statistics")
    public ApiResponse<ReportingDtos.OrganizerEventStatsResponse> getEventStatistics(@PathVariable UUID id) {
        return ApiResponse.ok(reportingService.getOrganizerEventStats(id));
    }
}
