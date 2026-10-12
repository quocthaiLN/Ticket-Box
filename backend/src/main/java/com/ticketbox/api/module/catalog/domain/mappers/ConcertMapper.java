package com.ticketbox.api.module.catalog.domain.mappers;

import com.ticketbox.api.module.catalog.domain.dtos.AdminConcertResponse;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertDetailResponse;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertResponse;
import com.ticketbox.api.module.catalog.domain.dtos.CreateConcertRequest;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.shared.storage.StorageService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public abstract class ConcertMapper {

    @Autowired
    protected StorageService storageService;

    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerName", source = "organizer.fullName")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl", qualifiedByName = "buildPublicUrl")
    @Mapping(target = "seatMapUrl", source = "seatMapUrl", qualifiedByName = "buildPublicUrl")
    @Mapping(target = "startsAt", source = "startsAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "endsAt", source = "endsAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "status", expression = "java(concert.getStatus() != null ? concert.getStatus().name() : null)")
    public abstract AdminConcertResponse toAdminResponse(Concert concert);

    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerName", source = "organizer.fullName")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl", qualifiedByName = "buildPublicUrl")
    @Mapping(target = "seatMapUrl", source = "seatMapUrl", qualifiedByName = "buildPublicUrl")
    @Mapping(target = "status", expression = "java(concert.getStatus() != null ? concert.getStatus().name() : null)")
    public abstract ConcertDetailResponse toDetailResponse(Concert concert);

    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerName", source = "organizer.fullName")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl", qualifiedByName = "buildPublicUrl")
    @Mapping(target = "ticketPriceRange", ignore = true)
    @Mapping(target = "status", expression = "java(concert.getStatus() != null ? concert.getStatus().name() : null)")
    public abstract ConcertResponse toResponse(Concert concert);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Concert toEntity(CreateConcertRequest request);

    @Named("buildPublicUrl")
    protected String buildPublicUrl(String relativePath) {
        return storageService != null ? storageService.buildPublicUrl(relativePath) : relativePath;
    }

    @Named("toUtcOffsetDateTime")
    protected OffsetDateTime toUtcOffsetDateTime(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
