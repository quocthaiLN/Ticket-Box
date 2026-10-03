package com.ticketbox.api.module.artistbio.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;

public final class ArtistBioJobNotFoundException extends NotFoundException {
    public ArtistBioJobNotFoundException() {
        super(ArtistBioErrorCode.ARTIST_BIO_JOB_NOT_FOUND, "Artist bio job not found", Map.of());
    }
}
