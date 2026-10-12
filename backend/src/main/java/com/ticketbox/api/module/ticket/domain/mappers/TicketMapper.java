package com.ticketbox.api.module.ticket.domain.mappers;

import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.ticket.domain.dtos.*;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.domain.entities.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "status", expression = "java(ticket.getStatus().name())")
    @Mapping(target = "concert", source = "concert")
    @Mapping(target = "ticketType", source = "ticketType")
    @Mapping(target = "seatZone", source = "seatZone")
    @Mapping(target = "qr", source = "ticket", qualifiedByName = "mapQrInfo")
    TicketResponse toTicketResponse(Ticket ticket);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    ConcertInfo toConcertInfo(Concert concert);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    TicketTypeInfo toTicketTypeInfo(TicketType ticketType);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    SeatZoneInfo toSeatZoneInfo(SeatZone seatZone);

    @Mapping(target = "ticketId", expression = "java(ticket.getId().toString())")
    @Mapping(target = "content", expression = "java(ticket.getId() + \".\" + ticket.getQrSignature())")
    TicketQrResponse toTicketQrResponse(Ticket ticket);

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("mapQrInfo")
    default QrInfo mapQrInfo(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        boolean qrAvailable = ticket.getStatus() == TicketStatus.ISSUED
                && ticket.getQrPayload() != null
                && ticket.getQrSignature() != null;
        return new QrInfo(qrAvailable, qrAvailable ? "/my-tickets/" + ticket.getId() + "/qr" : null);
    }
}
