package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.UnauthorizedException;
import java.util.Map;

public final class RevokedRefreshTokenException extends UnauthorizedException {
    public RevokedRefreshTokenException() {
        super(AuthErrorCode.TOKEN_REVOKED, "Refresh token has been revoked", Map.of());
    }
}
