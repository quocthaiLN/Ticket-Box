package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.BusinessRuleException;
import java.util.Map;

public final class TicketTypeNotOnSaleException extends BusinessRuleException {
    public TicketTypeNotOnSaleException() {
        super(OrderErrorCode.TICKET_TYPE_NOT_ON_SALE, "Ticket type is not on sale", Map.of());
    }
}
