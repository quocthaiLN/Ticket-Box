package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConcertRequest {

    @JsonIgnore private Set<String> suppliedFields = new HashSet<>();
    @JsonIgnore public boolean wasSupplied(String field) { return suppliedFields != null && suppliedFields.contains(field); }
    @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String field, Object value) { UnknownAdminField.reject(field); }

    public void setTitle(String value) { title = value; suppliedFields.add("title"); }
    public void setVenue(String value) { venue = value; suppliedFields.add("venue"); }
    public void setDescription(String value) { description = value; suppliedFields.add("description"); }
    @JsonProperty("artist_name") public void setArtistName(String value) { artistName = value; suppliedFields.add("artist_name"); }
    @JsonProperty("artist_bio") public void setArtistBio(String value) { artistBio = value; suppliedFields.add("artist_bio"); }
    @JsonProperty("starts_at") public void setStartsAt(LocalDateTime value) { startsAt = value; suppliedFields.add("starts_at"); }
    @JsonProperty("ends_at") public void setEndsAt(LocalDateTime value) { endsAt = value; suppliedFields.add("ends_at"); }
    @JsonProperty("cover_image_url") public void setCoverImageUrl(String value) { coverImageUrl = value; suppliedFields.add("cover_image_url"); }
    @JsonProperty("seat_map_url") public void setSeatMapUrl(String value) { seatMapUrl = value; suppliedFields.add("seat_map_url"); }

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
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime startsAt;

    @JsonProperty("ends_at")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime endsAt;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("seat_map_url")
    private String seatMapUrl;
}
