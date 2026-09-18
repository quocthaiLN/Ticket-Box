package com.ticketbox.api.module.ticket.domain.dtos;

import java.time.LocalDateTime;

public record TicketResponse(
                String id,
                String status,
                LocalDateTime issuedAt,
                ConcertInfo concert,
                TicketTypeInfo ticketType,
                SeatZoneInfo seatZone,
                QrInfo qr) {
}