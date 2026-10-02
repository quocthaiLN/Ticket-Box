package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class ConcertStateConflictException extends ConflictException {
    public ConcertStateConflictException() {
        super(CatalogErrorCode.INVALID_CONCERT_STATE,
                "Concert state does not allow this operation", Map.of());
    }
}
