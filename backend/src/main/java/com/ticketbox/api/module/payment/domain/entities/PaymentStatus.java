package com.ticketbox.api.module.payment.domain.entities;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED
}