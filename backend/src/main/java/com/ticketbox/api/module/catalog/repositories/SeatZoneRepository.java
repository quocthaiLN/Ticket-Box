package com.ticketbox.api.module.catalog.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.catalog.domain.entities.SeatZone;

import java.util.UUID;

public interface SeatZoneRepository extends JpaRepository<SeatZone, UUID> {

}





