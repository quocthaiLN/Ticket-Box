package com.ticketbox.api.module.payment.domain.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentGatewayRequest(
        UUID orderId,
        String orderInfo,
        BigDecimal amount,
        String currency,
        String returnUrl,
        String txnRef,
        String ipAddress,
        String locale,
        String bankCode,
        String orderType,
        String extraData) {
}
