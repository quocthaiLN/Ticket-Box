package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;
import java.util.UUID;

public final class SeatZoneNotFoundException extends NotFoundException {
    public SeatZoneNotFoundException(UUID seatZoneId) {
        super(CatalogErrorCode.SEAT_ZONE_NOT_FOUND, "Seat zone not found",
                Map.of("seat_zone_id", seatZoneId.toString()));
    }
}
