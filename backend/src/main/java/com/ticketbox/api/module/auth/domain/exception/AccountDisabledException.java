package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.UnauthorizedException;
import java.util.Map;

public final class AccountDisabledException extends UnauthorizedException {
    public AccountDisabledException() {
        super(AuthErrorCode.ACCOUNT_DISABLED, "Account is disabled or locked", Map.of());
    }
}
