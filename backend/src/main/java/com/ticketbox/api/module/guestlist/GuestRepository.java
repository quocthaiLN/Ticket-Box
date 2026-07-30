package com.ticketbox.api.module.guestlist;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.Guest;

import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

}





