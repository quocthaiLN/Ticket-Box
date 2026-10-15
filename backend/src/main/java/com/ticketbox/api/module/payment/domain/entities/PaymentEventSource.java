package com.ticketbox.api.module.payment.domain.entities;

public enum PaymentEventSource {
    IPN,
    RETURN_URL,
    QUERY_API,
    INTERNAL
}
