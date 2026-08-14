package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.catalog.domain.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PublicConcertService {

    Page<ConcertResponse> getPublishedConcerts(String q, String city, LocalDateTime from, LocalDateTime to, Pageable pageable);

    ConcertDetailResponse getConcertDetail(UUID concertId);

    ConcertMetadataResponse getConcertMetadata(UUID concertId);

    SeatMapResponse getConcertSeatMap(UUID concertId);

    List<TicketTypeResponse> getTicketTypes(UUID concertId, boolean includeClosed);

    InventoryResponse getInventory(UUID concertId);
}
