package com.ticketbox.api.module.catalog.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;
import java.util.Objects;

public final class TicketTypeNameAlreadyExistsException extends ConflictException {
    public TicketTypeNameAlreadyExistsException(String name) {
        super(CatalogErrorCode.TICKET_TYPE_NAME_ALREADY_EXISTS, "Ticket type name already exists",
                Map.of("name", Objects.toString(name, "")));
    }
}
