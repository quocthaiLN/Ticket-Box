package com.ticketbox.api.module.ticket.events;

import java.util.List;
import java.util.UUID;

public record TicketIssuedEvent(UUID orderId, UUID userId, List<UUID> ticketIds) {
}
