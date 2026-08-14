package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConcertRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 255, message = "Venue must not exceed 255 characters")
    private String venue;

    private String description;

    @Size(max = 255, message = "Artist name must not exceed 255 characters")
    @JsonProperty("artist_name")
    private String artistName;

    @JsonProperty("artist_bio")
    private String artistBio;

    @JsonProperty("starts_at")
    private LocalDateTime startsAt;

    @JsonProperty("ends_at")
    private LocalDateTime endsAt;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("seat_map_url")
    private String seatMapUrl;
}
