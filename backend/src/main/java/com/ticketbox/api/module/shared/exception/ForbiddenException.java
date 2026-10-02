package com.ticketbox.api.module.shared.exception;

import java.util.Map;

public abstract class ForbiddenException extends BusinessException {
    protected ForbiddenException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, ErrorType.FORBIDDEN, message, details);
    }
}
