package com.ticketbox.api.module.payment.domain.dtos;

/**
 * Provider-independent result of applying a verified callback to a payment.
 */
public enum CallbackHandlingResult {
    INVALID_SIGNATURE,
    PAYMENT_NOT_FOUND,
    ALREADY_PROCESSED,
    AMOUNT_MISMATCH,
    PROCESSED
}
