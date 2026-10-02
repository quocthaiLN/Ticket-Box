package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class InvalidOtpException extends BusinessRuleException {
    public InvalidOtpException() {
        super(AuthErrorCode.INVALID_OTP, "Invalid or expired OTP code", Map.of());
    }
}
