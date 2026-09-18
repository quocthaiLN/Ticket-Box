package com.ticketbox.api.module.ticket.repositories;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.ticket.domain.entities.Ticket;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"concert", "ticketType", "seatZone"})
    Page<Ticket> findByUserId(UUID userId, Pageable pageable);


    Optional<Ticket> findByIdAndUserId(UUID id, UUID userId);
}