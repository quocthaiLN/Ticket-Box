package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ConcertService {

    Concert getConcertForArtistBioUpload(User currentUser, UUID concertId);

    Concert getConcertForArtistBioUploadForUpdate(User currentUser, UUID concertId);

    void checkArtistBioAccess(User currentUser, UUID concertId);

    Page<AdminConcertResponse> getConcerts(User currentUser, String status, String q, Pageable pageable);

    AdminConcertMetadataResponse getConcertMetadata(User currentUser, UUID concertId);

    AdminConcertResponse createConcert(User currentUser, CreateConcertRequest request);

    AdminConcertResponse updateConcert(User currentUser, UUID concertId, UpdateConcertRequest request);

    AdminConcertResponse publishConcert(User currentUser, UUID concertId);

    AdminConcertResponse cancelConcert(User currentUser, UUID concertId, String reason);

    AdminSeatZoneResponse createSeatZone(User currentUser, UUID concertId, CreateSeatZoneRequest request);

    AdminSeatZoneResponse updateSeatZone(User currentUser, UUID seatZoneId, UpdateSeatZoneRequest request);

    AdminTicketTypeResponse createTicketType(User currentUser, UUID concertId, CreateTicketTypeRequest request);

    AdminTicketTypeResponse updateTicketType(User currentUser, UUID ticketTypeId, UpdateTicketTypeRequest request);
}
