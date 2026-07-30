package com.ticketbox.api.module.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.inventory.TicketType;

import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

}
