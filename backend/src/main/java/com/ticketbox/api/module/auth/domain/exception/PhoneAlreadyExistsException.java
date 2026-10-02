package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class PhoneAlreadyExistsException extends ConflictException {
    public PhoneAlreadyExistsException() {
        super(AuthErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered", Map.of());
    }
}
