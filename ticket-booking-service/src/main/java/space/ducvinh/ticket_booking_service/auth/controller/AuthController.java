package space.ducvinh.ticket_booking_service.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.auth.dto.AuthDtos;
import space.ducvinh.ticket_booking_service.auth.service.AuthService;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.security.JwtAuthenticationFilter;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final AppProperties appProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> register(
            @Valid @RequestBody AuthDtos.RegisterRequest req,
            HttpServletResponse response) {
        AuthDtos.AuthResponse auth = authService.register(req);
        writeAuthCookies(response, auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Registration successful", auth));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest req,
            HttpServletResponse response) {
        AuthDtos.AuthResponse auth = authService.login(req);
        writeAuthCookies(response, auth);
        return ApiResponse.ok("Login successful", auth);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.AuthResponse> refresh(
            @RequestBody(required = false) AuthDtos.RefreshRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawRefresh = (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isBlank())
                ? req.getRefreshToken()
                : extractCookie(request, REFRESH_COOKIE_NAME);
        AuthDtos.AuthResponse auth = authService.refresh(rawRefresh);
        writeAuthCookies(response, auth);
        return ApiResponse.ok("Token refreshed", auth);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) AuthDtos.RefreshRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawRefresh = (req != null && req.getRefreshToken() != null)
                ? req.getRefreshToken()
                : extractCookie(request, REFRESH_COOKIE_NAME);
        authService.logout(rawRefresh);
        clearAuthCookies(response);
        return ApiResponse.okMessage("Logged out successfully");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ApiResponse.okMessage("If the email exists, a password reset link has been sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.okMessage("Password reset successfully");
    }

    private void writeAuthCookies(HttpServletResponse response, AuthDtos.AuthResponse auth) {
        boolean secure = appProperties.getSecurity().isCookieSecure();
        ResponseCookie accessCookie = ResponseCookie.from(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, auth.getAccessToken())
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(appProperties.getSecurity().getAccessTokenTtlSeconds())
                .sameSite("Lax")
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, auth.getRefreshToken())
                .httpOnly(true)
                .secure(secure)
                .path("/api/v1/auth")
                .maxAge(appProperties.getSecurity().getRefreshTokenTtlSeconds())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        boolean secure = appProperties.getSecurity().isCookieSecure();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, "")
                .httpOnly(true).secure(secure).path("/").maxAge(0).sameSite("Lax").build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true).secure(secure).path("/api/v1/auth").maxAge(0).sameSite("Lax").build().toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
