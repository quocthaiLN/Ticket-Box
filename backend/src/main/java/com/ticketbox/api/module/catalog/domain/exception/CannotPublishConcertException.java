package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class CannotPublishConcertException extends BusinessRuleException {
    public CannotPublishConcertException(String reason) {
        super(CatalogErrorCode.CANNOT_PUBLISH_CONCERT, reason, Map.of());
    }
}
