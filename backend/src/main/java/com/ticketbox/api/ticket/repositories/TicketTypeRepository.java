package com.ticketbox.api.ticket.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.ticket.domain.entities.TicketType;

import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

}
