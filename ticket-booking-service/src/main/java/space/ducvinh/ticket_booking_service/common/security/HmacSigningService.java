package space.ducvinh.ticket_booking_service.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;

@Component
public class HmacSigningService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final AppProperties appProperties;

    public HmacSigningService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String signQrPayload(String payload) {
        return sign(payload, appProperties.getSecurity().getQrHmacSecret());
    }

    public boolean verifyQrToken(String token) {
        return verify(token, appProperties.getSecurity().getQrHmacSecret());
    }

    public String extractQrPayload(String token) {
        return extractPayload(token);
    }

    public String signAdmissionPayload(String payload) {
        return sign(payload, appProperties.getSecurity().getAdmissionTokenSecret());
    }

    public boolean verifyAdmissionToken(String token) {
        return verify(token, appProperties.getSecurity().getAdmissionTokenSecret());
    }

    public String extractAdmissionPayload(String token) {
        return extractPayload(token);
    }

    public String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC calculation failed", e);
        }
    }

    public String hmacSha512Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-512 calculation failed", e);
        }
    }

    private String sign(String payload, String secret) {
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha256Base64(encodedPayload, secret);
        return encodedPayload + "." + signature;
    }

    private boolean verify(String token, String secret) {
        if (token == null || !token.contains(".")) {
            return false;
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        String expectedSig = hmacSha256Base64(parts[0], secret);
        return MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8));
    }

    private String extractPayload(String token) {
        String[] parts = token.split("\\.", 2);
        return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    }

    private String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
