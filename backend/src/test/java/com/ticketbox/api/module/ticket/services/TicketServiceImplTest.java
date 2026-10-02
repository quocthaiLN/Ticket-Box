package com.ticketbox.api.module.ticket.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.domain.entities.TicketStatus;
import com.ticketbox.api.module.ticket.domain.exception.TicketNotFoundException;
import com.ticketbox.api.module.ticket.domain.exception.TicketQrUnavailableException;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {
    @Mock private TicketRepository ticketRepository;
    @InjectMocks private TicketServiceImpl ticketService;

    @Test
    void getMyTicketQr_missingTicketUsesTicketNotFoundException() {
        User user = User.builder().id(UUID.randomUUID()).build();
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findByIdAndUserId(ticketId, user.getId())).thenReturn(Optional.empty());

        TicketNotFoundException exception = assertThrows(TicketNotFoundException.class,
                () -> ticketService.getMyTicketQr(user, ticketId));

        assertEquals("TICKET_NOT_FOUND", exception.getErrorCode().code());
    }

    @Test
    void getMyTicketQr_unissuedTicketConflicts() {
        User user = User.builder().id(UUID.randomUUID()).build();
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().id(ticketId).status(TicketStatus.ISSUED).build();
        when(ticketRepository.findByIdAndUserId(ticketId, user.getId())).thenReturn(Optional.of(ticket));

        TicketQrUnavailableException exception = assertThrows(TicketQrUnavailableException.class,
                () -> ticketService.getMyTicketQr(user, ticketId));

        assertEquals("TICKET_QR_UNAVAILABLE", exception.getErrorCode().code());
    }
}
