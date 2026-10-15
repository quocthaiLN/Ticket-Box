package com.ticketbox.api.module.payment.domain.dtos;

import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import lombok.Builder;

@Builder
public record RefundGatewayResult(
        String providerRefundId,
        RefundStatus status,
        String responseCode,
        String message) {
}
