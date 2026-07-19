package com.ticketbox.api.concert.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.concert.domain.entities.ArtistBioJob;

import java.util.UUID;

public interface ArtistBioJobRepository extends JpaRepository<ArtistBioJob, UUID> {

}





