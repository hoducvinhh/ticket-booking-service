package space.ducvinh.ticket_booking_service.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.admin.dto.AdminDtos;
import space.ducvinh.ticket_booking_service.admin.service.AdminService;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.reporting.dto.ReportingDtos;
import space.ducvinh.ticket_booking_service.reporting.service.ReportingService;
import space.ducvinh.ticket_booking_service.user.dto.UserDtos;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ReportingService reportingService;

    @GetMapping("/overview")
    public ApiResponse<ReportingDtos.AdminOverviewStatsResponse> overview() {
        return ApiResponse.ok(reportingService.getAdminOverview());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserDtos.UserProfileResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listUsers(role, search, pageable)));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserDtos.UserProfileResponse> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.UpdateUserStatusRequest req) {
        return ApiResponse.ok("User status updated",
                adminService.updateUserStatus(SecurityUtils.requireCurrentUserId(), id, req.getStatus()));
    }

    @PostMapping("/users/{id}/roles")
    public ApiResponse<UserDtos.UserProfileResponse> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.AssignRoleRequest req) {
        return ApiResponse.ok("Role assigned",
                adminService.assignRole(SecurityUtils.requireCurrentUserId(), id, req.getRoleName()));
    }

    @GetMapping("/events")
    public ApiResponse<PageResponse<AdminDtos.AdminEventSummary>> listAllEvents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listAllEvents(pageable)));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<AdminDtos.AdminOrderSummary>> listAllOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listAllOrders(pageable)));
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<AdminDtos.AdminPaymentSummary>> listAllPayments(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listAllPayments(pageable)));
    }

    @GetMapping("/webhook-events")
    public ApiResponse<PageResponse<AdminDtos.AdminWebhookSummary>> listWebhooks(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listWebhooks(pageable)));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminDtos.AdminAuditLogSummary>> listAuditLogs(
            @PageableDefault(size = 30) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminService.listAuditLogs(pageable)));
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<AdminDtos.AdminOrderSummary> processRefund(@PathVariable UUID id) {
        return ApiResponse.ok("Refund processed",
                adminService.processRefund(SecurityUtils.requireCurrentUserId(), id));
    }
}
