package space.ducvinh.ticket_booking_service.payment.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.security.HmacSigningService;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.payment.enums.PaymentProvider;

@Component
@RequiredArgsConstructor
public class PaymentGatewayAdapter {

    private final AppProperties appProperties;
    private final HmacSigningService hmacSigningService;

    public GatewayPaymentInitResult initiatePayment(
            PaymentProvider provider,
            UUID paymentId,
            OrderEntity order) {
        String providerPaymentId = provider.name() + "-" + paymentId.toString().substring(0, 12).toUpperCase();
        String frontendBase = appProperties.getPayment().getFrontendBaseUrl();

        if (provider == PaymentProvider.PAYOS && hasPayOsCredentials()) {
            String data = "amount=" + order.getTotalAmount() + "&orderCode=" + order.getOrderCode();
            String sig = hmacSigningService.hmacSha256Hex(data, appProperties.getPayment().getPayos().getChecksumKey());
            String url = "https://pay.payos.vn/web/" + providerPaymentId + "?signature=" + sig;
            return new GatewayPaymentInitResult(providerPaymentId, url);
        }

        if (provider == PaymentProvider.VNPAY && hasVnPayCredentials()) {
            String data = "vnp_Amount=" + (order.getTotalAmount() * 100) + "&vnp_TxnRef=" + order.getOrderCode();
            String hash = hmacSigningService.hmacSha512Hex(data, appProperties.getPayment().getVnpay().getHashSecret());
            String url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef="
                    + URLEncoder.encode(order.getOrderCode(), StandardCharsets.UTF_8)
                    + "&vnp_SecureHash=" + hash;
            return new GatewayPaymentInitResult(providerPaymentId, url);
        }

        // Local Sandbox Simulator URL when real gateway credentials are absent
        String sandboxUrl = frontendBase + "/checkout/" + order.getId()
                + "?sandboxPaymentId=" + paymentId + "&provider=" + provider.name();
        return new GatewayPaymentInitResult(providerPaymentId, sandboxUrl);
    }

    public boolean verifyPayOsSignature(String orderCode, long amount, String status, String signature) {
        String key = appProperties.getPayment().getPayos().getChecksumKey();
        if (key == null || key.isBlank()) {
            key = appProperties.getSecurity().getJwtSecret();
        }
        String canonical = "amount=" + amount + "&orderCode=" + orderCode + "&status=" + status;
        String expected = hmacSigningService.hmacSha256Hex(canonical, key);
        return expected.equalsIgnoreCase(signature);
    }

    public boolean verifyVnPaySignature(String txnRef, long amount, String responseCode, String secureHash) {
        String key = appProperties.getPayment().getVnpay().getHashSecret();
        if (key == null || key.isBlank()) {
            key = appProperties.getSecurity().getJwtSecret();
        }
        String canonical = "vnp_Amount=" + amount + "&vnp_ResponseCode=" + responseCode + "&vnp_TxnRef=" + txnRef;
        String expected = hmacSigningService.hmacSha512Hex(canonical, key);
        return expected.equalsIgnoreCase(secureHash);
    }

    public String signPayOsWebhook(String orderCode, long amount, String status) {
        String key = appProperties.getPayment().getPayos().getChecksumKey();
        if (key == null || key.isBlank()) {
            key = appProperties.getSecurity().getJwtSecret();
        }
        return hmacSigningService.hmacSha256Hex("amount=" + amount + "&orderCode=" + orderCode + "&status=" + status, key);
    }

    private boolean hasPayOsCredentials() {
        return !appProperties.getPayment().getPayos().getChecksumKey().isBlank()
                && !appProperties.getPayment().getPayos().getClientId().isBlank();
    }

    private boolean hasVnPayCredentials() {
        return !appProperties.getPayment().getVnpay().getTmnCode().isBlank()
                && !appProperties.getPayment().getVnpay().getHashSecret().isBlank();
    }

    public record GatewayPaymentInitResult(String providerPaymentId, String paymentUrl) {
    }
}
