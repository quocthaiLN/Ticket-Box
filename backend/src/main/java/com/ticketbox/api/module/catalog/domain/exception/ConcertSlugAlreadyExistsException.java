package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;
import java.util.Objects;

public final class ConcertSlugAlreadyExistsException extends ConflictException {
    public ConcertSlugAlreadyExistsException(String slug) {
        super(CatalogErrorCode.SLUG_ALREADY_EXISTS, "Concert slug already exists",
                Map.of("slug", Objects.toString(slug, "")));
    }
}
