package com.ticketbox.api.module.ticket.domain.dtos;

import java.math.BigDecimal;

public record TicketTypeInfo(
        String id,
        String name,
        BigDecimal price,
        String currency) {
}