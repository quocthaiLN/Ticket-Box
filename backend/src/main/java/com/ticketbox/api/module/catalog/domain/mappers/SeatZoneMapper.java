package com.ticketbox.api.module.catalog.domain.mappers;

import com.ticketbox.api.module.catalog.domain.dtos.AdminSeatZoneResponse;
import com.ticketbox.api.module.catalog.domain.dtos.CreateSeatZoneRequest;
import com.ticketbox.api.module.catalog.domain.dtos.SeatZoneResponse;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SeatZoneMapper {

    @Mapping(target = "concertId", source = "concert.id")
    AdminSeatZoneResponse toAdminResponse(SeatZone zone);

    @Mapping(target = "concertId", source = "concert.id")
    SeatZoneResponse toResponse(SeatZone zone);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "concert", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SeatZone toEntity(CreateSeatZoneRequest request);
}
