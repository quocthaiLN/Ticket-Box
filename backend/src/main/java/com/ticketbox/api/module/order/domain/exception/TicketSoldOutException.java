package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class TicketSoldOutException extends ConflictException {
    public TicketSoldOutException() {
        super(OrderErrorCode.TICKET_SOLD_OUT, "Insufficient ticket inventory", Map.of());
    }
}
