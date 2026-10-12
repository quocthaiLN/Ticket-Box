package com.ticketbox.api.module.catalog.domain.mappers;

import com.ticketbox.api.module.catalog.domain.dtos.AdminTicketTypeResponse;
import com.ticketbox.api.module.catalog.domain.dtos.CreateTicketTypeRequest;
import com.ticketbox.api.module.catalog.domain.dtos.TicketTypeResponse;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface TicketTypeMapper {

    @Mapping(target = "concertId", source = "concert.id")
    @Mapping(target = "seatZoneId", source = "seatZone.id")
    @Mapping(target = "zoneCode", source = "seatZone.code")
    @Mapping(target = "saleStartAt", source = "saleStartAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "saleEndAt", source = "saleEndAt", qualifiedByName = "toUtcOffsetDateTime")
    @Mapping(target = "status", expression = "java(tt.getStatus() != null ? tt.getStatus().name() : null)")
    AdminTicketTypeResponse toAdminResponse(TicketType tt);

    @Mapping(target = "concertId", source = "concert.id")
    @Mapping(target = "seatZoneId", source = "seatZone.id")
    @Mapping(target = "zoneCode", source = "seatZone.code")
    @Mapping(target = "status", expression = "java(tt.getStatus() != null ? tt.getStatus().name() : null)")
    TicketTypeResponse toResponse(TicketType tt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "concert", ignore = true)
    @Mapping(target = "seatZone", ignore = true)
    @Mapping(target = "heldQuantity", ignore = true)
    @Mapping(target = "soldQuantity", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TicketType toEntity(CreateTicketTypeRequest request);

    @Named("toUtcOffsetDateTime")
    default OffsetDateTime toUtcOffsetDateTime(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
