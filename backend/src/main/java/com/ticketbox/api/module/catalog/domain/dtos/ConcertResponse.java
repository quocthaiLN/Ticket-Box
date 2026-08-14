package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertResponse {

    private UUID id;
    private String title;
    private String slug;
    private String venue;

    @JsonProperty("artist_name")
    private String artistName;

    @JsonProperty("starts_at")
    private LocalDateTime startsAt;

    @JsonProperty("ends_at")
    private LocalDateTime endsAt;

    private String status;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("organizer_id")
    private UUID organizerId;

    @JsonProperty("organizer_name")
    private String organizerName;

    @JsonProperty("ticket_price_range")
    private TicketPriceRange ticketPriceRange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketPriceRange {
        @JsonProperty("min_amount")
        private BigDecimal minAmount;

        @JsonProperty("max_amount")
        private BigDecimal maxAmount;

        private String currency;
    }
}
