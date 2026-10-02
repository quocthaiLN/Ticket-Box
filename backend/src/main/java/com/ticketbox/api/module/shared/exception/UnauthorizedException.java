package com.ticketbox.api.module.shared.exception;

import java.util.Map;

public abstract class UnauthorizedException extends BusinessException {
    protected UnauthorizedException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, ErrorType.UNAUTHORIZED, message, details);
    }
}
