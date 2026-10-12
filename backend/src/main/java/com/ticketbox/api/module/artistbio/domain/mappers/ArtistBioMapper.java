package com.ticketbox.api.module.artistbio.domain.mappers;

import com.ticketbox.api.module.artistbio.domain.dtos.ArtistBioJobResponse;
import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ArtistBioMapper {

    @Mapping(target = "concertId", source = "concert.id")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toUtcOffset")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "toUtcOffset")
    ArtistBioJobResponse toArtistBioJobResponse(ArtistBioJob job);

    @Named("toUtcOffset")
    default OffsetDateTime toUtcOffset(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atOffset(ZoneOffset.UTC) : null;
    }
}
