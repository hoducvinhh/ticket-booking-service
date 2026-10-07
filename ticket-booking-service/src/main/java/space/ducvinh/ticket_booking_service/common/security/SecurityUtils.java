package space.ducvinh.ticket_booking_service.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.UnauthorizedException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserPrincipal> getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static AppUserPrincipal requireCurrentPrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    public static UUID requireCurrentUserId() {
        return requireCurrentPrincipal().getUserId();
    }

    public static void requireOwnerOrAdmin(UUID ownerId) {
        AppUserPrincipal principal = requireCurrentPrincipal();
        if (principal.hasRole("ADMIN")) {
            return;
        }
        if (!principal.getUserId().equals(ownerId)) {
            throw new ForbiddenException("You do not have permission to access this resource");
        }
    }
}
