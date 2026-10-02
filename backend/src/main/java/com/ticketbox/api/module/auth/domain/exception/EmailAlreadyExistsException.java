package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException() {
        super(AuthErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered", Map.of());
    }
}
