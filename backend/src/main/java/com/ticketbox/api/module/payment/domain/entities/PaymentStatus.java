package com.ticketbox.api.module.payment.domain.entities;

public enum PaymentStatus {
    CREATING,
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED
}
