package com.ticketbox.api.module.ticket.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.ticket.domain.dtos.TicketQrResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {

    Page<TicketResponse> getMyTickets(User currentUser, Pageable pageable);

    TicketQrResponse getMyTicketQr(User currentUser, UUID ticketId);
}
