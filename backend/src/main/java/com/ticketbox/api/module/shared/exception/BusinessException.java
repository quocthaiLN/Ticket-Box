package com.ticketbox.api.module.shared.exception;

import java.util.Map;
import java.util.Objects;

import lombok.Getter;


@Getter 
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    protected BusinessException(ErrorCode errorCode, ErrorType expectedType, String message,
                                Map<String, Object> details) {
        super(Objects.requireNonNull(message, "message"), null, false, false);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        if (errorCode.code() == null || errorCode.code().isBlank()
                || errorCode.type() != Objects.requireNonNull(expectedType, "expectedType")) {
            throw new IllegalArgumentException("Business error code does not match exception type");
        }
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
}
