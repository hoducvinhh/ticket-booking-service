package space.ducvinh.ticket_booking_service.user.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.auth.repository.RefreshTokenRepository;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.user.dto.UserDtos;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserDtos.UserProfileResponse getProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserDtos.UserProfileResponse.from(user);
    }

    @Transactional
    public UserDtos.UserProfileResponse updateProfile(UUID userId, UserDtos.UpdateProfileRequest req) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFullName(req.getFullName().trim());
        user.setPhone(req.getPhone() != null ? req.getPhone().trim() : null);
        return UserDtos.UserProfileResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID userId, UserDtos.ChangePasswordRequest req) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }
}
