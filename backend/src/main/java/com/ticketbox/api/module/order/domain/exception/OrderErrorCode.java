package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(ErrorType.NOT_FOUND),
    ORDER_ACCESS_DENIED(ErrorType.FORBIDDEN),
    ORDER_NOT_SETTLABLE(ErrorType.CONFLICT),
    TICKET_TYPE_NOT_ON_SALE(ErrorType.UNPROCESSABLE),
    SALE_WINDOW_CLOSED(ErrorType.UNPROCESSABLE),
    TICKET_SOLD_OUT(ErrorType.CONFLICT),
    PER_USER_LIMIT_EXCEEDED(ErrorType.CONFLICT);

    private final ErrorType type;

    OrderErrorCode(ErrorType type) { this.type = type; }
    @Override public String code() { return name(); }
    @Override public ErrorType type() { return type; }
}
