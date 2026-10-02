package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class AlreadyVerifiedException extends ConflictException {
    public AlreadyVerifiedException(String message) {
        super(AuthErrorCode.ALREADY_VERIFIED, message, Map.of());
    }
}
