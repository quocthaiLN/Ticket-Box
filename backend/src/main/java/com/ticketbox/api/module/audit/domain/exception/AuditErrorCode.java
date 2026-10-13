package com.ticketbox.api.module.audit.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum AuditErrorCode implements ErrorCode {
    AUDIT_LOG_NOT_FOUND(ErrorType.NOT_FOUND);

    private final ErrorType type;

    AuditErrorCode(ErrorType type) {
        this.type = type;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorType type() {
        return type;
    }
}
