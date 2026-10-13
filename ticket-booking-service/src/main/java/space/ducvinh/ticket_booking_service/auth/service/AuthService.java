package space.ducvinh.ticket_booking_service.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.auth.dto.AuthDtos;
import space.ducvinh.ticket_booking_service.auth.entity.PasswordResetTokenEntity;
import space.ducvinh.ticket_booking_service.auth.entity.RefreshTokenEntity;
import space.ducvinh.ticket_booking_service.auth.repository.PasswordResetTokenRepository;
import space.ducvinh.ticket_booking_service.auth.repository.RefreshTokenRepository;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.UnauthorizedException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.security.JwtTokenProvider;
import space.ducvinh.ticket_booking_service.user.dto.UserDtos;
import space.ducvinh.ticket_booking_service.user.entity.RoleEntity;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.RoleName;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.RoleRepository;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;
    private final OutboxService outboxService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("EMAIL_EXISTS", "Email is already registered");
        }

        Set<RoleEntity> roles = new HashSet<>();
        roles.add(requireRole(RoleName.CUSTOMER.name()));
        if (req.isOrganizer()) {
            roles.add(requireRole(RoleName.ORGANIZER.name()));
        }

        UserEntity user = UserEntity.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();
        user = userRepository.save(user);

        outboxService.enqueue("USER", user.getId(), "USER_REGISTERED", Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "fullName", user.getFullName()
        ));

        return issueAuthTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        UserEntity user = userRepository.findByEmailIgnoreCase(req.getEmail().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is locked or disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueAuthTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }
        String hash = JwtTokenProvider.sha256Hex(rawRefreshToken);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existing.getRevokedAt() != null || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired or been revoked");
        }

        UserEntity user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is locked or disabled");
        }

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        return issueAuthTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = JwtTokenProvider.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest req) {
        userRepository.findByEmailIgnoreCase(req.getEmail().trim()).ifPresent(user -> {
            String rawToken = generateOpaqueToken();
            passwordResetTokenRepository.save(PasswordResetTokenEntity.builder()
                    .userId(user.getId())
                    .tokenHash(JwtTokenProvider.sha256Hex(rawToken))
                    .expiresAt(Instant.now().plusSeconds(1800))
                    .build());

            outboxService.enqueue("USER", user.getId(), "PASSWORD_RESET_REQUESTED", Map.of(
                    "userId", user.getId().toString(),
                    "email", user.getEmail(),
                    "resetToken", rawToken
            ));
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest req) {
        String hash = JwtTokenProvider.sha256Hex(req.getToken().trim());
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid password reset token"));

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Password reset token has expired or already been used");
        }

        UserEntity user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
    }

    private AuthDtos.AuthResponse issueAuthTokens(UserEntity user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toSet());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        String rawRefreshToken = generateOpaqueToken();

        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .userId(user.getId())
                .tokenHash(JwtTokenProvider.sha256Hex(rawRefreshToken))
                .expiresAt(Instant.now().plusSeconds(appProperties.getSecurity().getRefreshTokenTtlSeconds()))
                .build());

        return AuthDtos.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .expiresInSeconds(appProperties.getSecurity().getAccessTokenTtlSeconds())
                .user(UserDtos.UserProfileResponse.from(user))
                .build();
    }

    private RoleEntity requireRole(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + name));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
