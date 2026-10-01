package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateConcertRequest {

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) { UnknownAdminField.reject(field); }

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Slug is required")
    @Size(max = 255, message = "Slug must not exceed 255 characters")
    private String slug;

    @NotBlank(message = "Venue is required")
    @Size(max = 255, message = "Venue must not exceed 255 characters")
    private String venue;

    private String description;

    @NotBlank(message = "Artist name is required")
    @Size(max = 255, message = "Artist name must not exceed 255 characters")
    @JsonProperty("artist_name")
    private String artistName;

    @JsonProperty("artist_bio")
    private String artistBio;

    @NotNull(message = "Starts at time is required")
    @JsonProperty("starts_at")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime startsAt;

    @NotNull(message = "Ends at time is required")
    @JsonProperty("ends_at")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime endsAt;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("seat_map_url")
    private String seatMapUrl;
}
