package space.ducvinh.ticket_booking_service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.ducvinh.ticket_booking_service.user.entity.RoleEntity;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;

public class UserDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileResponse {
        private UUID id;
        private String email;
        private String fullName;
        private String phone;
        private String status;
        private Set<String> roles;
        private Instant createdAt;

        public static UserProfileResponse from(UserEntity user) {
            return UserProfileResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .phone(user.getPhone())
                    .status(user.getStatus().name())
                    .roles(user.getRoles().stream().map(RoleEntity::getName).collect(java.util.stream.Collectors.toSet()))
                    .createdAt(user.getCreatedAt())
                    .build();
        }
    }

    @Data
    public static class UpdateProfileRequest {
        @NotBlank(message = "Full name is required")
        @Size(max = 255)
        private String fullName;

        @Size(max = 50)
        private String phone;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 100, message = "New password must be at least 8 characters")
        private String newPassword;
    }
}
