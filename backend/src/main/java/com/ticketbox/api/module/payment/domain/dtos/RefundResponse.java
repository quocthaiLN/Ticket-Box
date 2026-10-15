package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundResponse(
        UUID id,
        @JsonProperty("payment_id") UUID paymentId,
        BigDecimal amount,
        String currency,
        @JsonProperty("idempotency_key") String idempotencyKey,
        RefundStatus status,
        @JsonProperty("provider_refund_id") String providerRefundId,
        String reason,
        @JsonProperty("requested_by") UUID requestedBy,
        @JsonProperty("failure_code") String failureCode,
        @JsonProperty("failure_message") String failureMessage,
        @JsonProperty("requested_at") LocalDateTime requestedAt,
        @JsonProperty("completed_at") LocalDateTime completedAt
) {}
