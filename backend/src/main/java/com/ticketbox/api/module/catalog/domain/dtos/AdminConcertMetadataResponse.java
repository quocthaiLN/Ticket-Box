package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminConcertMetadataResponse(
        AdminConcertResponse concert,
        @JsonProperty("seat_zones") List<AdminSeatZoneResponse> seatZones,
        @JsonProperty("ticket_types") List<AdminTicketTypeResponse> ticketTypes,
        @JsonProperty("seat_map") ConcertMetadataResponse.SeatMapInfo seatMap,
        @JsonProperty("artist_bio") String artistBio) {
}
