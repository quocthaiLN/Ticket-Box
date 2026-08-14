package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {

    @JsonProperty("concert_id")
    private UUID concertId;

    @JsonProperty("svg_url")
    private String svgUrl;

    @JsonProperty("fallback_image_url")
    private String fallbackImageUrl;

    private List<SeatZoneResponse> zones;
}
