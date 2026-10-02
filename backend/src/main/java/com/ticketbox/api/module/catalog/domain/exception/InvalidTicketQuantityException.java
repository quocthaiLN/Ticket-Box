package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class InvalidTicketQuantityException extends BusinessRuleException {
    public InvalidTicketQuantityException() {
        this("Ticket quantity does not satisfy the catalog rules");
    }

    public InvalidTicketQuantityException(String message) {
        super(CatalogErrorCode.INVALID_QUANTITY, message, Map.of());
    }
}
