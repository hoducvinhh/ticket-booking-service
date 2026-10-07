package space.ducvinh.ticket_booking_service.common.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.response.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Pattern WAITING_ROOM_JOIN = Pattern.compile("^/api/v1/events/([^/]+)/waiting-room/join$");
    private static final Pattern RESERVATION_CREATE = Pattern.compile("^/api/v1/events/([^/]+)/reservations$");
    private static final Pattern ORDER_CHECKOUT = Pattern.compile("^/api/v1/orders/([^/]+)/checkout$");

    private final ProxyManager<String> proxyManager;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!appProperties.getRateLimit().isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<RateLimitRule> ruleOpt = matchRule(request);
        if (ruleOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = ruleOpt.get();
        try {
            BucketConfiguration config = BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(rule.limit())
                            .refillIntervally(rule.limit(), Duration.ofMinutes(1))
                            .build())
                    .build();
            Bucket bucket = proxyManager.builder().build(rule.redisKey(), () -> config);
            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorResponse err = ErrorResponse.builder()
                        .success(false)
                        .status(HttpStatus.TOO_MANY_REQUESTS.value())
                        .errorCode("RATE_LIMIT_EXCEEDED")
                        .message("Too many requests. Please slow down.")
                        .path(request.getRequestURI())
                        .build();
                response.getWriter().write(objectMapper.writeValueAsString(err));
                return;
            }
        } catch (Exception ex) {
            // Fail-open if Redis rate limiter is temporarily unreachable
            log.warn("Rate limiter fallback open for {}: {}", rule.redisKey(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private Optional<RateLimitRule> matchRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String userOrIp = SecurityUtils.getCurrentPrincipal()
                .map(p -> p.getUserId().toString())
                .orElseGet(() -> extractClientIp(request));

        if ("/api/v1/auth/login".equals(uri)) {
            return Optional.of(new RateLimitRule("rate-limit:" + extractClientIp(request) + ":login", 5));
        }

        Matcher wr = WAITING_ROOM_JOIN.matcher(uri);
        if (wr.matches()) {
            return Optional.of(new RateLimitRule("rate-limit:" + userOrIp + ":wr-join:" + wr.group(1), 3));
        }

        if (RESERVATION_CREATE.matcher(uri).matches()) {
            return Optional.of(new RateLimitRule("rate-limit:" + userOrIp + ":reservation", 5));
        }

        if (ORDER_CHECKOUT.matcher(uri).matches()) {
            return Optional.of(new RateLimitRule("rate-limit:" + userOrIp + ":checkout", 5));
        }

        if ("/api/v1/check-in/scan".equals(uri)) {
            return Optional.of(new RateLimitRule("rate-limit:" + userOrIp + ":qr-scan", 30));
        }

        return Optional.empty();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitRule(String redisKey, int limit) {
    }
}
