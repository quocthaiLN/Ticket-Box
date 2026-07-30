package com.ticketbox.api.module.guestlist;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.guestlist.GuestImportError;

import java.util.UUID;

public interface GuestImportErrorRepository extends JpaRepository<GuestImportError, UUID> {

}





