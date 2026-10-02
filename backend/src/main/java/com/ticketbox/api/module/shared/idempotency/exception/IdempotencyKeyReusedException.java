package com.ticketbox.api.module.shared.idempotency.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;

import java.util.Map;

public final class IdempotencyKeyReusedException extends ConflictException {
    public IdempotencyKeyReusedException() {
        super(IdempotencyErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency key was already used for a different request", Map.of());
    }
}
