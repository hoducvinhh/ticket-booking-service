package space.ducvinh.ticket_booking_service.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;

@Component
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
        byte[] keyBytes = appProperties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(appProperties.getSecurity().getAccessTokenTtlSeconds());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public AppUserPrincipal toPrincipal(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        List<String> roleList = claims.get("roles", List.class);
        Set<String> roles = roleList == null ? Set.of() : roleList.stream().collect(Collectors.toSet());
        return new AppUserPrincipal(userId, email, "", "ACTIVE", roles);
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
