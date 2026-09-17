package com.ticketbox.api.module.payment.domain.dtos;

/**
 * Verified data returned by a payment provider callback. Payment state may be
 * updated only when {@link #signatureValid()} is true.
 */
public record PaymentVerificationResult(
        boolean signatureValid,
        String transactionReference,
        String providerTransactionId,
        String responseCode,
        String transactionStatus,
        String amount) {

    public boolean successful() {
        return signatureValid
                && "00".equals(responseCode)
                && "00".equals(transactionStatus);
    }
}
