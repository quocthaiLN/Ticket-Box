package com.ticketbox.api.module.payment.domain.dtos;

import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;

@Builder
public record PaymentOutcome(
        PaymentProvider provider,
        PaymentEventSource source,
        String dedupeKey,
        String merchantTxnRef,
        String providerTxnId,
        PaymentStatus targetStatus,
        BigDecimal amount,
        boolean signatureValid,
        String failureCode,
        String failureMessage,
        Map<String, ?> rawPayload) {
}
