package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum CatalogErrorCode implements ErrorCode {
    CONCERT_NOT_FOUND(ErrorType.NOT_FOUND),
    SEAT_ZONE_NOT_FOUND(ErrorType.NOT_FOUND),
    TICKET_TYPE_NOT_FOUND(ErrorType.NOT_FOUND),
    SLUG_ALREADY_EXISTS(ErrorType.CONFLICT),
    SEAT_ZONE_CODE_ALREADY_EXISTS(ErrorType.CONFLICT),
    TICKET_TYPE_NAME_ALREADY_EXISTS(ErrorType.CONFLICT),
    INVALID_CONCERT_STATE(ErrorType.CONFLICT),
    CANNOT_PUBLISH_CONCERT(ErrorType.UNPROCESSABLE),
    INVALID_CONCERT_TIME_RANGE(ErrorType.UNPROCESSABLE),
    INVALID_QUANTITY(ErrorType.UNPROCESSABLE),
    INVALID_SALE_WINDOW(ErrorType.UNPROCESSABLE),
    ZONE_CAPACITY_EXCEEDED(ErrorType.UNPROCESSABLE),
    FORBIDDEN(ErrorType.FORBIDDEN);

    private final ErrorType type;

    CatalogErrorCode(ErrorType type) {
        this.type = type;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorType type() {
        return type;
    }
}
