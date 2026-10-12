package com.ticketbox.api.module.ticket.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.ticket.domain.dtos.ConcertInfo;
import com.ticketbox.api.module.ticket.domain.dtos.QrInfo;
import com.ticketbox.api.module.ticket.domain.dtos.SeatZoneInfo;
import com.ticketbox.api.module.ticket.domain.dtos.TicketQrResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketTypeInfo;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.domain.entities.TicketStatus;
import com.ticketbox.api.module.ticket.domain.exception.TicketNotFoundException;
import com.ticketbox.api.module.ticket.domain.exception.TicketQrUnavailableException;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

        private final TicketRepository ticketRepository;
        private final com.ticketbox.api.module.ticket.domain.mappers.TicketMapper ticketMapper;

        @Transactional(readOnly = true)
        @Override
        public Page<TicketResponse> getMyTickets(User currentUser, Pageable pageable) {
                return ticketRepository.findByUserId(currentUser.getId(), pageable)
                                .map(ticketMapper::toTicketResponse);
        }

        @Transactional(readOnly = true)
        @Override
        public TicketQrResponse getMyTicketQr(User currentUser, UUID ticketId) {
                Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, currentUser.getId())
                                .orElseThrow(TicketNotFoundException::new);

                if (ticket.getStatus() != TicketStatus.ISSUED) {
                        throw new TicketQrUnavailableException("QR code is unavailable for this ticket");
                }
                if (ticket.getQrPayload() == null || ticket.getQrSignature() == null) {
                        throw new TicketQrUnavailableException("QR code has not been issued for this ticket");
                }
                return ticketMapper.toTicketQrResponse(ticket);
        }
}
