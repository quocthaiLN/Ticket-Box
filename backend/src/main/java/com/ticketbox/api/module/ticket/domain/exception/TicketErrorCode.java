package com.ticketbox.api.module.ticket.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum TicketErrorCode implements ErrorCode {
    TICKET_NOT_FOUND(ErrorType.NOT_FOUND),
    TICKET_QR_UNAVAILABLE(ErrorType.CONFLICT);

    private final ErrorType type;
    TicketErrorCode(ErrorType type) { this.type = type; }
    @Override public String code() { return name(); }
    @Override public ErrorType type() { return type; }
}
