package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminConcertResponse {
    private UUID id;
    private String title;
    private String slug;
    private String venue;
    private String description;
    @JsonProperty("artist_name") private String artistName;
    @JsonProperty("artist_bio") private String artistBio;
    @JsonProperty("starts_at") private OffsetDateTime startsAt;
    @JsonProperty("ends_at") private OffsetDateTime endsAt;
    private String status;
    @JsonProperty("cover_image_url") private String coverImageUrl;
    @JsonProperty("seat_map_url") private String seatMapUrl;
    @JsonProperty("organizer_id") private UUID organizerId;
    @JsonProperty("organizer_name") private String organizerName;
    @JsonProperty("created_at") private OffsetDateTime createdAt;
    @JsonProperty("updated_at") private OffsetDateTime updatedAt;
}
