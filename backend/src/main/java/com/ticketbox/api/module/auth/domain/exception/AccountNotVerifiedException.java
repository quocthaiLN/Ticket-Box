package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.UnauthorizedException;
import java.util.Map;

public final class AccountNotVerifiedException extends UnauthorizedException {
    public AccountNotVerifiedException() {
        super(AuthErrorCode.ACCOUNT_NOT_VERIFIED, "Account has not been verified via OTP", Map.of());
    }
}
