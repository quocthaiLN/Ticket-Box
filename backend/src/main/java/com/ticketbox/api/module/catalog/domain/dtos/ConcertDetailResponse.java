package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ConcertDetailResponse {

    private UUID id;
    private String title;
    private String slug;
    private String venue;
    private String description;

    @JsonProperty("artist_name")
    private String artistName;

    @JsonProperty("artist_bio")
    private String artistBio;

    @JsonProperty("starts_at")
    private LocalDateTime startsAt;

    @JsonProperty("ends_at")
    private LocalDateTime endsAt;

    private String status;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("seat_map_url")
    private String seatMapUrl;

    @JsonProperty("organizer_id")
    private UUID organizerId;

    @JsonProperty("organizer_name")
    private String organizerName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
