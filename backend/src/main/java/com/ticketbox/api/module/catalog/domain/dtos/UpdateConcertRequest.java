package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConcertRequest {

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        UnknownAdminField.reject(field);
    }

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Builder.Default
    private JsonNullable<String> title = JsonNullable.undefined();

    @Size(max = 255, message = "Venue must not exceed 255 characters")
    @Builder.Default
    private JsonNullable<String> venue = JsonNullable.undefined();

    @Builder.Default
    private JsonNullable<String> description = JsonNullable.undefined();

    @Size(max = 255, message = "Artist name must not exceed 255 characters")
    @JsonProperty("artist_name")
    @Builder.Default
    private JsonNullable<String> artistName = JsonNullable.undefined();

    @JsonProperty("artist_bio")
    @Builder.Default
    private JsonNullable<String> artistBio = JsonNullable.undefined();

    @JsonProperty("starts_at")
    @JsonDeserialize(contentUsing = Rfc3339UtcLocalDateTimeDeserializer.class)
    @Builder.Default
    private JsonNullable<LocalDateTime> startsAt = JsonNullable.undefined();

    @JsonProperty("ends_at")
    @JsonDeserialize(contentUsing = Rfc3339UtcLocalDateTimeDeserializer.class)
    @Builder.Default
    private JsonNullable<LocalDateTime> endsAt = JsonNullable.undefined();

    @JsonProperty("cover_image_url")
    @Builder.Default
    private JsonNullable<String> coverImageUrl = JsonNullable.undefined();

    @JsonProperty("seat_map_url")
    @Builder.Default
    private JsonNullable<String> seatMapUrl = JsonNullable.undefined();
}
