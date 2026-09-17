package com.ticketbox.api.module.payment.controllers;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.MomoIpnRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.services.PaymentService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(userDetails.getUser(), paymentId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        CreatePaymentResponse response = paymentService.createPayment(
                userDetails.getUser(), idempotencyKey.toString(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<PaymentCallbackResponse> vnpayIpn(@RequestParam Map<String, String> parameters) {
        return ResponseEntity.ok(paymentService.handleVnpayIpn(parameters));
    }

    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> momoIpn(@Valid @RequestBody MomoIpnRequest request) {
        paymentService.handleMomoIpn(request);
        return ResponseEntity.noContent().build();
    }

    private UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY",
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

    private AppException invalidIdempotencyKey() {
        return new AppException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be a UUID v4");
    }
}
