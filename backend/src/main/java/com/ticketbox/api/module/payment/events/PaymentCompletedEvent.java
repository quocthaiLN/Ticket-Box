package com.ticketbox.api.module.payment.events;

import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId,
        PaymentProvider provider,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String providerTransactionId) {
}
