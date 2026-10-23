package space.ducvinh.ticket_booking_service.waitingroom.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.waitingroom.dto.WaitingRoomDtos;
import space.ducvinh.ticket_booking_service.waitingroom.service.WaitingRoomService;

@RestController
@RequestMapping("/api/v1/events/{id}/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    @PostMapping("/join")
    public ApiResponse<WaitingRoomDtos.WaitingRoomStatusResponse> join(@PathVariable UUID id) {
        return ApiResponse.ok(waitingRoomService.joinQueue(id, SecurityUtils.requireCurrentUserId()));
    }

    @GetMapping("/status")
    public ApiResponse<WaitingRoomDtos.WaitingRoomStatusResponse> status(@PathVariable UUID id) {
        return ApiResponse.ok(waitingRoomService.getStatus(id, SecurityUtils.requireCurrentUserId()));
    }

    @PostMapping("/leave")
    public ApiResponse<Void> leave(@PathVariable UUID id) {
        waitingRoomService.leaveQueue(id, SecurityUtils.requireCurrentUserId());
        return ApiResponse.okMessage("Left waiting room");
    }
}
