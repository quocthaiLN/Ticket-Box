package com.ticketbox.api.module.guestlist.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.domain.entities.Guest;

import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

}





