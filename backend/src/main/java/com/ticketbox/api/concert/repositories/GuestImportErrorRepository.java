package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.GuestImportError;

import java.util.UUID;

public interface GuestImportErrorRepository extends JpaRepository<GuestImportError, UUID> {

}





