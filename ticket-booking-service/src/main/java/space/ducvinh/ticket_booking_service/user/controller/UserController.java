package space.ducvinh.ticket_booking_service.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.user.dto.UserDtos;
import space.ducvinh.ticket_booking_service.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserDtos.UserProfileResponse> getMe() {
        return ApiResponse.ok(userService.getProfile(SecurityUtils.requireCurrentUserId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDtos.UserProfileResponse> updateMe(@Valid @RequestBody UserDtos.UpdateProfileRequest req) {
        return ApiResponse.ok("Profile updated", userService.updateProfile(SecurityUtils.requireCurrentUserId(), req));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UserDtos.ChangePasswordRequest req) {
        userService.changePassword(SecurityUtils.requireCurrentUserId(), req);
        return ApiResponse.okMessage("Password changed successfully");
    }
}
