package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;
import java.util.UUID;

public final class ConcertNotFoundException extends NotFoundException {
    public ConcertNotFoundException(UUID concertId) {
        super(CatalogErrorCode.CONCERT_NOT_FOUND, "Concert not found",
                Map.of("concert_id", concertId.toString()));
    }
}
