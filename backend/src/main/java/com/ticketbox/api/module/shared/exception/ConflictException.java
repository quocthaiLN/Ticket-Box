package com.ticketbox.api.module.shared.exception;

import java.util.Map;

public abstract class ConflictException extends BusinessException {
    protected ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, ErrorType.CONFLICT, message, details);
    }
}
