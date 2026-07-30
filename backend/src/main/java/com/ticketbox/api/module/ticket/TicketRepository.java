package com.ticketbox.api.module.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.ticket.Ticket;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

}





