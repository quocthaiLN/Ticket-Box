package com.ticketbox.api.module.artistbio.domain.exception;

/** Contains only safe messages suitable for the job response. */
public final class ArtistBioProcessingException extends RuntimeException {
    private final boolean retryable;
    public ArtistBioProcessingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
    public boolean isRetryable() { return retryable; }
}
