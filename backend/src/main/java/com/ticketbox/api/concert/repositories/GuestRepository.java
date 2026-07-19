package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.Guest;

import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

}





