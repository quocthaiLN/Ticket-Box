package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class SaleWindowClosedException extends BusinessRuleException {
    public SaleWindowClosedException() {
        super(OrderErrorCode.SALE_WINDOW_CLOSED, "Ticket type is outside its sale window", Map.of());
    }
}
