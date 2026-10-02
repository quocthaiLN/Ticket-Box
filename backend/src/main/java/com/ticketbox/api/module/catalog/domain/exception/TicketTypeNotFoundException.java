package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;
import java.util.UUID;

public final class TicketTypeNotFoundException extends NotFoundException {
    public TicketTypeNotFoundException(UUID ticketTypeId) {
        super(CatalogErrorCode.TICKET_TYPE_NOT_FOUND, "Ticket type not found",
                Map.of("ticket_type_id", ticketTypeId.toString()));
    }
}
