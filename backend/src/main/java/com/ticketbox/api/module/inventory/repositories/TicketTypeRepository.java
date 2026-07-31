package com.ticketbox.api.module.inventory.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.inventory.domain.entities.TicketType;

import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

}
