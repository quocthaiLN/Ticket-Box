package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.UnauthorizedException;
import java.util.Map;

public final class ExpiredRefreshTokenException extends UnauthorizedException {
    public ExpiredRefreshTokenException() {
        super(AuthErrorCode.TOKEN_EXPIRED, "Refresh token is invalid or expired", Map.of());
    }
}
