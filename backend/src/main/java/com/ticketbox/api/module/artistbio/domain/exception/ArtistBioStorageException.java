package com.ticketbox.api.module.artistbio.domain.exception;

import com.ticketbox.api.infrastructure.exception.AppException;
import org.springframework.http.HttpStatus;

/** Technical 503 adapter exception, matching shared infrastructure failures in this codebase. */
public final class ArtistBioStorageException extends AppException {
    public ArtistBioStorageException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ARTIST_BIO_STORAGE_UNAVAILABLE", "Artist bio storage is unavailable");
    }
}
