package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.ai.*;
import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioProcessingException;
import com.ticketbox.api.module.artistbio.storage.ArtistBioStorage;
import com.ticketbox.api.module.artistbio.services.ArtistBioJobStateService.Outcome;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("worker")
@RequiredArgsConstructor
@Slf4j
public class ArtistBioProcessor {
    private final ArtistBioJobStateService state;
    private final ArtistBioStorage storage;
    private final PdfTextExtractor extractor;
    private final ArtistBioGenerator generator;

    public Outcome process(UUID jobId) {
        var claim = state.claim(jobId);
        if (claim.outcome() != Outcome.PROCESS) return claim.outcome();
        String text;
        String bio;
        try {
            text = extractor.extract(storage.download(claim.source()));
            bio = generator.generate(text);
        } catch (ArtistBioProcessingException exception) {
            return state.fail(jobId, claim.token(), exception.getMessage(), exception.isRetryable());
        } catch (RuntimeException exception) {
            log.warn("Artist bio job {} failed unexpectedly ({})", jobId, exception.getClass().getSimpleName());
            return state.fail(jobId, claim.token(), "Unexpected processing failure", true);
        }
        // Kept outside the catch: a DB failure must leave the delivery unacknowledged.
        return state.complete(jobId, claim.token(), text, bio);
    }
}
