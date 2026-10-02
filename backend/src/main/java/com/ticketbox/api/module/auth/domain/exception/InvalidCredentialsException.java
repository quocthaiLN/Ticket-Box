package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.UnauthorizedException;
import java.util.Map;

public final class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS, "Invalid email or password", Map.of());
    }
}
