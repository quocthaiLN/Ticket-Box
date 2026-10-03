package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.domain.entities.*;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static com.ticketbox.api.module.artistbio.services.ArtistBioJobStateService.Outcome.*;

class ArtistBioJobStateServiceTest {
    private final ArtistBioJobRepository repository = mock(ArtistBioJobRepository.class);
    private final ArtistBioJobStateService service = new ArtistBioJobStateService(repository);
    private final UUID id = UUID.randomUUID();

    private ArtistBioJob job() {
        ArtistBioJob job = ArtistBioJob.builder().id(id).sourceFileUrl("s3://bucket/press-kits/file.pdf").build();
        when(repository.findForUpdate(id)).thenReturn(Optional.of(job));
        return job;
    }

    @Test void duplicateCannotClaimOrOverwriteCurrentAttempt() {
        ArtistBioJob job = job();
        var first = service.claim(id);
        assertThat(first.outcome()).isEqualTo(PROCESS);
        assertThat(service.claim(id).outcome()).isEqualTo(SKIP);
        assertThat(service.complete(id, UUID.randomUUID(), "text", "wrong")).isEqualTo(SKIP);
        assertThat(job.getGeneratedBio()).isNull();
        assertThat(service.complete(id, first.token(), "text", "draft")).isEqualTo(DONE);
        assertThat(job.getGeneratedBio()).isEqualTo("draft");
        assertThat(service.claim(id).outcome()).isEqualTo(SKIP);
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test void expiredLeaseIsReclaimedAndOldCompletionIgnored() {
        ArtistBioJob job = job();
        var old = service.claim(id);
        job.setLeaseUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertThat(service.complete(id, old.token(), "text", "late")).isEqualTo(SKIP);
        var current = service.claim(id);
        assertThat(current.token()).isNotEqualTo(old.token());
        assertThat(service.fail(id, old.token(), "stale", false)).isEqualTo(SKIP);
        assertThat(service.complete(id, current.token(), "text", "current")).isEqualTo(DONE);
    }

    @Test void transientErrorsRetryAtMostThreeTimes() {
        ArtistBioJob job = job();
        for (int i = 1; i <= 3; i++) {
            var claim = service.claim(id);
            assertThat(service.fail(id, claim.token(), "AI temporarily unavailable", true))
                    .isEqualTo(i < 3 ? RETRY : DEAD);
            if (i < 3) {
                assertThat(service.claim(id).outcome()).isEqualTo(SKIP);
                job.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
            }
        }
        assertThat(job.getStatus()).isEqualTo(ArtistBioJobStatus.FAILED);
        assertThat(service.claim(id).outcome()).isEqualTo(DEAD);
        assertThat(job.getAttempts()).isEqualTo(3);
    }

    @Test void finalCrashedAttemptBecomesFailed() {
        ArtistBioJob job = job();
        job.setAttempts(3);
        job.setStatus(ArtistBioJobStatus.PROCESSING);
        assertThat(service.claim(id).outcome()).isEqualTo(DEAD);
        assertThat(job.getStatus()).isEqualTo(ArtistBioJobStatus.FAILED);
    }

    @Test void permanentErrorDoesNotRetry() {
        ArtistBioJob job = job();
        var claim = service.claim(id);
        assertThat(service.fail(id, claim.token(), "PDF has no text", false)).isEqualTo(DEAD);
        assertThat(job.getNextAttemptAt()).isNull();
    }
}
