package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;

public record GatewayQueryResult(
        String merchantReference,
        BigDecimal amount,
        String providerTransactionId,
        PaymentStatus status,
        String resultCode) {
}
