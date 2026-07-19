package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.GuestImportJob;

import java.util.UUID;

public interface GuestImportJobRepository extends JpaRepository<GuestImportJob, UUID> {

}





