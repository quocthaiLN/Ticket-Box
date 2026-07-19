package com.ticketbox.api.ticket.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.ticket.domain.entities.Ticket;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

}





