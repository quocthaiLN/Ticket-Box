package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(ErrorType.CONFLICT),
    PHONE_ALREADY_EXISTS(ErrorType.CONFLICT),
    USER_NOT_FOUND(ErrorType.NOT_FOUND),
    INVALID_CREDENTIALS(ErrorType.UNAUTHORIZED),
    ACCOUNT_NOT_VERIFIED(ErrorType.UNAUTHORIZED),
    ACCOUNT_DISABLED(ErrorType.UNAUTHORIZED),
    TOKEN_EXPIRED(ErrorType.UNAUTHORIZED),
    TOKEN_REVOKED(ErrorType.UNAUTHORIZED),
    INVALID_OTP(ErrorType.UNPROCESSABLE),
    ALREADY_VERIFIED(ErrorType.CONFLICT);

    private final ErrorType type;

    AuthErrorCode(ErrorType type) {
        this.type = type;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorType type() {
        return type;
    }
}
