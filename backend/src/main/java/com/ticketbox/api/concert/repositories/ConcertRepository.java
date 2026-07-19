package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.Concert;

import java.util.UUID;

public interface ConcertRepository extends JpaRepository<Concert, UUID> {

}





