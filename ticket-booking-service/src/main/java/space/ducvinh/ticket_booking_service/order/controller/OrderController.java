package space.ducvinh.ticket_booking_service.order.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.common.response.PageResponse;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.order.dto.OrderDtos;
import space.ducvinh.ticket_booking_service.order.service.OrderService;
import space.ducvinh.ticket_booking_service.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderDtos.OrderResponse>> createOrder(
            @Valid @RequestBody OrderDtos.CreateOrderRequest req) {
        OrderDtos.OrderResponse created = orderService.createOrder(SecurityUtils.requireCurrentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Order created", created));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDtos.OrderResponse> getOrder(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.getOrder(id));
    }

    @GetMapping("/users/me/orders")
    public ApiResponse<PageResponse<OrderDtos.OrderResponse>> listMyOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                orderService.listMyOrders(SecurityUtils.requireCurrentUserId(), pageable)));
    }

    @PostMapping("/orders/{id}/checkout")
    public ApiResponse<OrderDtos.CheckoutResponse> checkout(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderDtos.CheckoutRequest req) {
        return ApiResponse.ok(paymentService.checkout(
                SecurityUtils.requireCurrentUserId(), id, idempotencyKey, req));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderDtos.OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ApiResponse.ok("Order cancelled", orderService.cancelOrder(id));
    }
}
