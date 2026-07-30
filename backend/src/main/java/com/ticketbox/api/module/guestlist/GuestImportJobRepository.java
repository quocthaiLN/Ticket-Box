package com.ticketbox.api.module.guestlist;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.GuestImportJob;

import java.util.UUID;

public interface GuestImportJobRepository extends JpaRepository<GuestImportJob, UUID> {

}





