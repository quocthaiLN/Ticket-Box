package com.ticketbox.api.module.ticket.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class TicketQrUnavailableException extends ConflictException {
    public TicketQrUnavailableException(String message) {
        super(TicketErrorCode.TICKET_QR_UNAVAILABLE, message, Map.of());
    }
}
