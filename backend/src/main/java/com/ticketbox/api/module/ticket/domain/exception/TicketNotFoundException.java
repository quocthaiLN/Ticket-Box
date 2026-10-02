package com.ticketbox.api.module.ticket.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;

public final class TicketNotFoundException extends NotFoundException {
    public TicketNotFoundException() { super(TicketErrorCode.TICKET_NOT_FOUND, "Ticket not found", Map.of()); }
}
