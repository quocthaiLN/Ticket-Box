package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class InvalidConcertTimeRangeException extends BusinessRuleException {
    public InvalidConcertTimeRangeException() {
        super(CatalogErrorCode.INVALID_CONCERT_TIME_RANGE,
                "Concert end time must be after start time", Map.of());
    }
}
