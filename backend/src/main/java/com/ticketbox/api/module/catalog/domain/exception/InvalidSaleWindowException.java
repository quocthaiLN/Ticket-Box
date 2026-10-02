package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class InvalidSaleWindowException extends BusinessRuleException {
    public InvalidSaleWindowException() {
        super(CatalogErrorCode.INVALID_SALE_WINDOW,
                "Sale end time must be after sale start time", Map.of());
    }
}
