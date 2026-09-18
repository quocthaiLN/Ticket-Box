package com.ticketbox.api.module.ticket.domain.dtos;

import java.time.LocalDateTime;

public record ConcertInfo(
        String id,
        String title,
        String artistName,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String coverImageUrl) {
}