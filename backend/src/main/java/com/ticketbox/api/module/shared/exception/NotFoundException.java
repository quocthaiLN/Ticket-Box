package com.ticketbox.api.module.shared.exception;

import java.util.Map;

public abstract class NotFoundException extends BusinessException {
    protected NotFoundException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, ErrorType.NOT_FOUND, message, details);
    }
}
