package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;
import java.util.Objects;

public final class SeatZoneCodeAlreadyExistsException extends ConflictException {
    public SeatZoneCodeAlreadyExistsException(String code) {
        super(CatalogErrorCode.SEAT_ZONE_CODE_ALREADY_EXISTS, "Seat zone code already exists",
                Map.of("seat_zone_code", Objects.toString(code, "")));
    }
}
