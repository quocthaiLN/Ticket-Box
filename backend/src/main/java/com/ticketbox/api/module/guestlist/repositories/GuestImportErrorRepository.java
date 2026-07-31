package com.ticketbox.api.module.guestlist.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.domain.entities.GuestImportError;

import java.util.UUID;

public interface GuestImportErrorRepository extends JpaRepository<GuestImportError, UUID> {

}





