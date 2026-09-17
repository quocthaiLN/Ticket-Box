package com.ticketbox.api.module.payment.domain.dtos;

import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A callback interpreted by one payment provider. Provider protocols are
 * normalized here; persistence remains the responsibility of PaymentService.
 */
public record GatewayCallback(
        boolean signatureValid,
        Optional<UUID> paymentId,
        Optional<BigDecimal> amount,
        String providerTransactionId,
        Optional<PaymentStatus> targetStatus,
        Optional<String> failureReason,
        Map<String, String> sanitizedPayload) {
}
