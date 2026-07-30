package com.ticketbox.api.module.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.catalog.Concert;

import java.util.UUID;

public interface ConcertRepository extends JpaRepository<Concert, UUID> {

}





