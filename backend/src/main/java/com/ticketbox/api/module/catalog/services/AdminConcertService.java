package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminConcertService {

    Page<ConcertDetailResponse> getAdminConcerts(User currentUser, String status, String q, Pageable pageable);

    ConcertDetailResponse createConcert(User currentUser, CreateConcertRequest request);

    ConcertDetailResponse updateConcert(User currentUser, UUID concertId, UpdateConcertRequest request);

    ConcertDetailResponse publishConcert(User currentUser, UUID concertId);

    ConcertDetailResponse cancelConcert(User currentUser, UUID concertId, String reason);

    SeatZoneResponse createSeatZone(User currentUser, UUID concertId, CreateSeatZoneRequest request);

    SeatZoneResponse updateSeatZone(User currentUser, UUID seatZoneId, UpdateSeatZoneRequest request);

    TicketTypeResponse createTicketType(User currentUser, UUID concertId, CreateTicketTypeRequest request);

    TicketTypeResponse updateTicketType(User currentUser, UUID ticketTypeId, UpdateTicketTypeRequest request);
}
