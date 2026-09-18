package com.ticketbox.api.module.ticket.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.ticket.domain.dtos.ConcertInfo;
import com.ticketbox.api.module.ticket.domain.dtos.QrInfo;
import com.ticketbox.api.module.ticket.domain.dtos.SeatZoneInfo;
import com.ticketbox.api.module.ticket.domain.dtos.TicketQrResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketTypeInfo;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.domain.entities.TicketStatus;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

        private final TicketRepository ticketRepository;

        @Transactional(readOnly = true)
        @Override
        public Page<TicketResponse> getMyTickets(User currentUser, Pageable pageable) {
                return ticketRepository.findByUserId(currentUser.getId(), pageable)
                                .map(this::toTicketResponse);
        }

        @Transactional(readOnly = true)
        @Override
        public TicketQrResponse getMyTicketQr(User currentUser, UUID ticketId) {
                Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, currentUser.getId())
                                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                                                "Ticket not found"));

                if (ticket.getStatus() != TicketStatus.ISSUED) {
                        throw new AppException(HttpStatus.CONFLICT, "TICKET_QR_UNAVAILABLE",
                                        "QR code is unavailable for this ticket");
                }
                if (ticket.getQrPayload() == null || ticket.getQrSignature() == null) {
                        throw new AppException(HttpStatus.CONFLICT, "TICKET_QR_UNAVAILABLE",
                                        "QR code has not been issued for this ticket");
                }
                return new TicketQrResponse(ticket.getId().toString(), ticket.getId() + "." + ticket.getQrSignature());
        }

        private TicketResponse toTicketResponse(Ticket ticket) {
                boolean qrAvailable = ticket.getStatus() == TicketStatus.ISSUED
                                && ticket.getQrPayload() != null
                                && ticket.getQrSignature() != null;

                return new TicketResponse(
                                ticket.getId().toString(),
                                ticket.getStatus().name(),
                                ticket.getIssuedAt(),
                                new ConcertInfo(
                                                ticket.getConcert().getId().toString(),
                                                ticket.getConcert().getTitle(),
                                                ticket.getConcert().getArtistName(),
                                                ticket.getConcert().getVenue(),
                                                ticket.getConcert().getStartsAt(),
                                                ticket.getConcert().getEndsAt(),
                                                ticket.getConcert().getCoverImageUrl()),
                                new TicketTypeInfo(
                                                ticket.getTicketType().getId().toString(),
                                                ticket.getTicketType().getName(),
                                                ticket.getTicketType().getPrice(),
                                                ticket.getTicketType().getCurrency()),
                                new SeatZoneInfo(
                                                ticket.getSeatZone().getId().toString(),
                                                ticket.getSeatZone().getCode(),
                                                ticket.getSeatZone().getName()),
                                new QrInfo(qrAvailable,
                                                qrAvailable ? "/my-tickets/" + ticket.getId() + "/qr" : null));
        }
}
