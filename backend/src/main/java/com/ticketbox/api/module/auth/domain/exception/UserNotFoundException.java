package com.ticketbox.api.module.auth.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;

public final class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super(AuthErrorCode.USER_NOT_FOUND, "User not found", Map.of());
    }
}
