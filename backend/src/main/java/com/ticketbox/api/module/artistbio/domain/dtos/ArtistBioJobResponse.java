package com.ticketbox.api.module.artistbio.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJob;
import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJobStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ArtistBioJobResponse(
        UUID id, 
        @JsonProperty("concert_id") UUID concertId,
        ArtistBioJobStatus status, 
        @JsonProperty("generated_bio") String generatedBio,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    public static ArtistBioJobResponse from(ArtistBioJob job) {
        return new ArtistBioJobResponse(job.getId(), job.getConcert().getId(), job.getStatus(),
                job.getGeneratedBio(), job.getErrorMessage(), job.getCreatedAt().atOffset(ZoneOffset.UTC),
                job.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
