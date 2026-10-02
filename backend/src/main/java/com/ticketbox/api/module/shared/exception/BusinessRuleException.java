package com.ticketbox.api.module.shared.exception;

import java.util.Map;

public abstract class BusinessRuleException extends BusinessException {
    protected BusinessRuleException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, ErrorType.UNPROCESSABLE, message, details);
    }
}
