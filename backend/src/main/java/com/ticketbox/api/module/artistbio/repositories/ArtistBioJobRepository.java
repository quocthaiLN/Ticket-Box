package com.ticketbox.api.module.artistbio.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJob;

import java.util.UUID;

public interface ArtistBioJobRepository extends JpaRepository<ArtistBioJob, UUID> {

}





