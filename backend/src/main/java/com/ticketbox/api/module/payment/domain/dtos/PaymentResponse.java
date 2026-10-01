package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentResponse(
        @JsonProperty("payment_id") UUID paymentId,
        @JsonProperty("order_id") UUID orderId,
        String provider,
        String status,
        BigDecimal amount,
        String currency,
        @JsonProperty("checkout_url") String checkoutUrl,
        @JsonProperty("hold_expires_at") LocalDateTime holdExpiresAt,
        @JsonProperty("paid_at") LocalDateTime paidAt,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("refund_required") boolean refundRequired,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
}
