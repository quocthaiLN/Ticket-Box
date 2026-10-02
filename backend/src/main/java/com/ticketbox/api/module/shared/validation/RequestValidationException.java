package com.ticketbox.api.module.shared.validation;

import java.util.Map;

public class RequestValidationException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;

    public RequestValidationException(String code, String message) {
        this(code, message, Map.of());
    }

    public RequestValidationException(String code, String message, Map<String, Object> details) {
        super(message, null, false, false);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
