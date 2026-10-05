package space.ducvinh.ticket_booking_service.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Security security = new Security();
    private Booking booking = new Booking();
    private Payment payment = new Payment();
    private Storage storage = new Storage();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";
    }

    @Getter
    @Setter
    public static class Security {
        private String jwtSecret;
        private String qrHmacSecret;
        private String admissionTokenSecret;
        private long accessTokenTtlSeconds = 900;
        private long refreshTokenTtlSeconds = 604800;
        private boolean cookieSecure = false;
    }

    @Getter
    @Setter
    public static class Booking {
        private long reservationTtlSeconds = 600;
        private int maxSeatsPerReservation = 6;
        private long waitingRoomAdmissionTtlSeconds = 600;
    }

    @Getter
    @Setter
    public static class Payment {
        private PayOs payos = new PayOs();
        private VnPay vnpay = new VnPay();
        private String frontendBaseUrl = "http://localhost:3000";

        @Getter
        @Setter
        public static class PayOs {
            private String clientId = "";
            private String apiKey = "";
            private String checksumKey = "";
        }

        @Getter
        @Setter
        public static class VnPay {
            private String tmnCode = "";
            private String hashSecret = "";
        }
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "./uploads";
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
    }
}
