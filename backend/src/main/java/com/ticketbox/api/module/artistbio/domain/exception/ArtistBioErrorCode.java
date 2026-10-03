package com.ticketbox.api.module.artistbio.domain.exception;

import com.ticketbox.api.module.shared.exception.ErrorCode;
import com.ticketbox.api.module.shared.exception.ErrorType;

public enum ArtistBioErrorCode implements ErrorCode {
    ARTIST_BIO_JOB_NOT_FOUND(ErrorType.NOT_FOUND);

    private final ErrorType type;
    ArtistBioErrorCode(ErrorType type) { this.type = type; }
    @Override public String code() { return name(); }
    @Override public ErrorType type() { return type; }
}
