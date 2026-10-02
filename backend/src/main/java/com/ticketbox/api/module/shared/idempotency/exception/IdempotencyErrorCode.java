package com.ticketbox.api.module.shared.idempotency.exception;


import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum IdempotencyErrorCode implements ErrorCode {
        IDEMPOTENCY_KEY_REUSED;
        @Override public String code() { return name(); }
        @Override public ErrorType type() { return ErrorType.CONFLICT; }
}