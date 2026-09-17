package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentCallbackResponse(
        @JsonProperty("RspCode") String responseCode,
        @JsonProperty("Message") String message) {

    public static PaymentCallbackResponse of(String responseCode, String message) {
        return new PaymentCallbackResponse(responseCode, message);
    }
}
