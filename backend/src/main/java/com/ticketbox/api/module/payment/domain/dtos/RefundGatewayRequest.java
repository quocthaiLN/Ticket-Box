package com.ticketbox.api.module.payment.domain.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundGatewayRequest(
        UUID refundId,
        String merchantTxnRef,
        String providerTxnId,
        BigDecimal amount,
        String reason,
        String transDate) {
}
