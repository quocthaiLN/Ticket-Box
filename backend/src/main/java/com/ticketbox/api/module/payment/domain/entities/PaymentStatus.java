package com.ticketbox.api.module.payment.domain.entities;

public enum PaymentStatus {
    INITIATED,
    CREATING,
    PENDING,
    SUCCESS,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED
}
