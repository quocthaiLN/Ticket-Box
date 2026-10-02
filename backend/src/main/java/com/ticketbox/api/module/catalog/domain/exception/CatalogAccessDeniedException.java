package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ForbiddenException;
import java.util.Map;

public final class CatalogAccessDeniedException extends ForbiddenException {
    public CatalogAccessDeniedException() {
        super(CatalogErrorCode.FORBIDDEN,
                "You do not have permission to access or modify this concert", Map.of());
    }
}
