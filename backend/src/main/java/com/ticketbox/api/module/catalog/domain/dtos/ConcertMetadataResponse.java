package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertMetadataResponse {

    private ConcertDetailResponse concert;

    @JsonProperty("seat_zones")
    private List<SeatZoneResponse> seatZones;

    @JsonProperty("ticket_types")
    private List<TicketTypeResponse> ticketTypes;

    @JsonProperty("seat_map")
    private SeatMapInfo seatMap;

    @JsonProperty("artist_bio")
    private String artistBio;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatMapInfo {
        @JsonProperty("svg_url")
        private String svgUrl;

        @JsonProperty("fallback_image_url")
        private String fallbackImageUrl;
    }
}
