package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MomoCreatePaymentResponse(
        Integer resultCode,
        String message,
        String payUrl) {
}
