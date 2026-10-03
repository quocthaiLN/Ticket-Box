package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.domain.dtos.ArtistBioJobResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ArtistBioService {
    ArtistBioJobResponse upload(User actor, UUID concertId, MultipartFile file);
    ArtistBioJobResponse get(User actor, UUID concertId, UUID jobId);
}
