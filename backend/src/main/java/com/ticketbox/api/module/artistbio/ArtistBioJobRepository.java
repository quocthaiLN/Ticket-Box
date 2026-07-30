package com.ticketbox.api.module.artistbio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.artistbio.ArtistBioJob;

import java.util.UUID;

public interface ArtistBioJobRepository extends JpaRepository<ArtistBioJob, UUID> {

}





