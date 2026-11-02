package space.ducvinh.ticket_booking_service.payment.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.payment.dto.PaymentDtos;
import space.ducvinh.ticket_booking_service.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments/{id}")
    public ApiResponse<PaymentDtos.PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.getPayment(id));
    }

    @PostMapping("/webhooks/payos")
    public ApiResponse<Void> payOsWebhook(@Valid @RequestBody PaymentDtos.PayOsWebhookRequest req) {
        paymentService.handlePayOsWebhook(req);
        return ApiResponse.okMessage("Webhook processed");
    }

    @PostMapping("/webhooks/vnpay")
    public ApiResponse<Void> vnPayWebhook(@Valid @RequestBody PaymentDtos.VnPayWebhookRequest req) {
        paymentService.handleVnPayWebhook(req);
        return ApiResponse.okMessage("Webhook processed");
    }

    @PostMapping("/sandbox-payments/{paymentId}/complete")
    public ApiResponse<PaymentDtos.PaymentResponse> completeSandbox(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) PaymentDtos.SandboxCompleteRequest req) {
        return ApiResponse.ok("Sandbox payment completed", paymentService.completeSandboxPayment(paymentId, req));
    }
}
