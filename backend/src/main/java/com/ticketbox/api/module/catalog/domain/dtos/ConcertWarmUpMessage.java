package com.ticketbox.api.module.catalog.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertWarmUpMessage {
    private UUID concertId;
    private LocalDateTime startsAt;
}
