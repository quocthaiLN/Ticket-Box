package com.ticketbox.api.module.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.catalog.SeatZone;

import java.util.UUID;

public interface SeatZoneRepository extends JpaRepository<SeatZone, UUID> {

}





