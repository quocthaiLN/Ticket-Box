package com.ticketbox.api.module.guestlist.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.domain.entities.GuestImportJob;

import java.util.UUID;

public interface GuestImportJobRepository extends JpaRepository<GuestImportJob, UUID> {

}





