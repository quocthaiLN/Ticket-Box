package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class ZoneCapacityExceededException extends BusinessRuleException {
    public ZoneCapacityExceededException() {
        super(CatalogErrorCode.ZONE_CAPACITY_EXCEEDED,
                "Ticket quantities exceed seat zone capacity", Map.of());
    }
}
