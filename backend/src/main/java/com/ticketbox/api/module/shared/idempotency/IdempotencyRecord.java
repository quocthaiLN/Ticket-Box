package com.ticketbox.api.module.shared.idempotency;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record IdempotencyRecord(
        IdempotencyState state,
        String fingerprint,
        IdempotencyOutcome outcome,
        JsonNode response,
        @JsonProperty("http_status") Integer httpStatus,
        @JsonProperty("error_code") String errorCode,
        String message,
        JsonNode details) {
}