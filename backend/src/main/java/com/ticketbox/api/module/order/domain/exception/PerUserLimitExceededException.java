package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class PerUserLimitExceededException extends ConflictException {
    public PerUserLimitExceededException() {
        super(OrderErrorCode.PER_USER_LIMIT_EXCEEDED, "Ticket purchase limit per user would be exceeded", Map.of());
    }
}
