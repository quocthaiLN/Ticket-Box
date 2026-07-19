package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.SeatZone;

import java.util.UUID;

public interface SeatZoneRepository extends JpaRepository<SeatZone, UUID> {

}





