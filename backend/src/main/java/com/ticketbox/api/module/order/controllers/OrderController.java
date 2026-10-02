package com.ticketbox.api.module.order.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import com.ticketbox.api.module.order.services.OrderService;
import com.ticketbox.api.module.payment.domain.dtos.CreateOrderPaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.services.PaymentService;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        OrderResponse response = orderService.createHeldOrder(
                userDetails.getUser(), idempotencyKey.toString(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw new RequestValidationException("MISSING_IDEMPOTENCY_KEY",
                    "Idempotency-Key header is required");
        }
        try {
            UUID idempotencyKey = UUID.fromString(header);
            if (idempotencyKey.version() != 4) {
                throw invalidIdempotencyKey();
            }
            return idempotencyKey;
        } catch (IllegalArgumentException exception) {
            throw invalidIdempotencyKey();
        }
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
       @AuthenticationPrincipal CustomUserDetails userDetails,
       @PathVariable UUID orderId
    ) {
        OrderResponse response = orderService.getOrder(userDetails.getUser(), orderId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(response));
    }

    @PostMapping("/{orderId}/payments")
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createOrderPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderPaymentRequest request,
            HttpServletRequest httpRequest) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setProvider(request.getProvider());
        paymentRequest.setBankCode(request.getBankCode());
        paymentRequest.setLocale(request.getLocale());

        CreatePaymentResponse response = paymentService.createPayment(userDetails.getUser(),
                idempotencyKey.toString(), paymentRequest, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
    
    private RequestValidationException invalidIdempotencyKey() {
        return new RequestValidationException("INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be a UUID v4");
    }
}
