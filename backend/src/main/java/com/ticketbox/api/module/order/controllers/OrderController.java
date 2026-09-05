package com.ticketbox.api.module.order.controllers;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderResponse;
import com.ticketbox.api.module.order.services.OrderIdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final OrderIdempotencyService orderIdempotencyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
    public ResponseEntity<ApiResponse<HeldOrderResponse>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        ApiResponse<HeldOrderResponse> response = orderIdempotencyService.createOrReplay(
                userDetails.getUser(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
