package com.ticketbox.api.module.shared.exception;

import java.util.Map;

/** Reconstructs a stored business outcome without coupling the domain contract to HTTP. */
public final class ReplayedBusinessException extends BusinessException {
    public ReplayedBusinessException(String code, ErrorType type, String message, Map<String, Object> details) {
        super(new StoredErrorCode(code, type), type, message, details);
    }

    private record StoredErrorCode(String code, ErrorType type) implements ErrorCode { }
}
