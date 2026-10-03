package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.domain.entities.*;
import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioJobNotFoundException;
import com.ticketbox.api.module.artistbio.messaging.ArtistBioProducer;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import com.ticketbox.api.module.artistbio.storage.ArtistBioStorage;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.entities.*;
import com.ticketbox.api.module.catalog.domain.entities.*;
import com.ticketbox.api.module.catalog.domain.exception.*;
import com.ticketbox.api.module.catalog.services.ConcertService;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtistBioServiceTest {
    private final ConcertService concertService = mock(ConcertService.class);
    private final ArtistBioJobRepository jobs = mock(ArtistBioJobRepository.class);
    private final ArtistBioStorage storage = mock(ArtistBioStorage.class);
    private final ArtistBioProducer producer = mock(ArtistBioProducer.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final User actor = User.builder().id(UUID.randomUUID()).role(UserRole.ORGANIZER).build();
    private final Concert concert = Concert.builder().id(UUID.randomUUID()).organizer(actor)
            .status(ConcertStatus.PUBLISHED).artistBio("existing bio").build();
    private final AbstractPlatformTransactionManager tx = new AbstractPlatformTransactionManager() {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    };
    private final ArtistBioService service = new ArtistBioServiceImpl(concertService, jobs, storage, producer, audit, new TransactionTemplate(tx));
    private final MockMultipartFile file = new MockMultipartFile("file", "kit.pdf", "application/pdf", "%PDF-1.7".getBytes());
    @BeforeEach void setup() {
        when(concertService.getConcertForArtistBioUpload(concert.getOrganizer(), concert.getId())).thenReturn(concert);
        when(concertService.getConcertForArtistBioUploadForUpdate(concert.getOrganizer(), concert.getId())).thenReturn(concert);
        when(storage.upload(eq(concert.getId()), any(), any())).thenReturn("s3://bucket/object.pdf");
        when(jobs.saveAndFlush(any())).thenAnswer(call -> {
            ArtistBioJob job = call.getArgument(0);
            job.setConcert(concert); job.setId(UUID.randomUUID()); job.setCreatedAt(LocalDateTime.now()); job.setUpdatedAt(LocalDateTime.now());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return job;
        });
    }
    @Test void uploadsAndAuditsThenPublishesAfterCommitWithoutChangingConcert() {
        doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return null; })
                .when(producer).generate(any());
        var result = service.upload(actor, concert.getId(), file);
        assertThat(result.status()).isEqualTo(ArtistBioJobStatus.PENDING);
        verify(audit).logAction(eq(actor), eq("CREATE_ARTIST_BIO_JOB"), eq("ARTIST_BIO_JOB"),
                eq(result.id().toString()), anyMap(), isNull(), isNull());
        verify(producer).generate(result.id());
        verify(storage, never()).delete(any());
        assertThat(concert.getArtistBio()).isEqualTo("existing bio");
    }
    @Test void rollbackRemovesUploadedObjectAndDoesNotPublish() {
        doThrow(new IllegalStateException("audit failed")).when(audit).logAction(any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.upload(actor, concert.getId(), file)).isInstanceOf(IllegalStateException.class);
        verify(storage).delete("s3://bucket/object.pdf"); verifyNoInteractions(producer);
    }
    @Test void brokerFailureLeavesDurablePendingJobAndFile() {
        doThrow(new IllegalStateException()).when(producer).generate(any());
        assertThat(service.upload(actor, concert.getId(), file).status()).isEqualTo(ArtistBioJobStatus.PENDING);
        verify(storage, never()).delete(any());
    }
    @Test void rejectsOtherOrganizerAndAudienceBeforeStorage() {
        var other = User.builder().id(UUID.randomUUID()).role(UserRole.ORGANIZER).build();
        when(concertService.getConcertForArtistBioUpload(other, concert.getId())).thenThrow(new CatalogAccessDeniedException());
        assertThatThrownBy(() -> service.upload(other, concert.getId(), file)).isInstanceOf(CatalogAccessDeniedException.class);
        other.setRole(UserRole.AUDIENCE);
        doThrow(new CatalogAccessDeniedException()).when(concertService).checkArtistBioAccess(other, concert.getId());
        assertThatThrownBy(() -> service.get(other, concert.getId(), UUID.randomUUID())).isInstanceOf(CatalogAccessDeniedException.class);
        verifyNoInteractions(storage, jobs, producer);
    }
    @Test void adminMayUploadButCompletedConcertCannotAcceptNewJobs() {
        var admin = User.builder().id(UUID.randomUUID()).role(UserRole.ADMIN).build();
        assertThat(service.upload(admin, concert.getId(), file).status()).isEqualTo(ArtistBioJobStatus.PENDING);
        concert.setStatus(ConcertStatus.COMPLETED);
        when(concertService.getConcertForArtistBioUpload(admin, concert.getId())).thenThrow(new ConcertStateConflictException());
        assertThatThrownBy(() -> service.upload(admin, concert.getId(), file)).isInstanceOf(ConcertStateConflictException.class);
    }
    @Test void pollChecksConcertAndJobPairEvenAfterCancellation() {
        concert.setStatus(ConcertStatus.CANCELED);
        UUID jobId = UUID.randomUUID();
        doNothing().when(concertService).checkArtistBioAccess(actor, concert.getId());
        when(jobs.findByIdAndConcertId(jobId, concert.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(actor, concert.getId(), jobId)).isInstanceOf(ArtistBioJobNotFoundException.class);
        ArtistBioJob job = ArtistBioJob.builder().id(jobId).concert(concert).status(ArtistBioJobStatus.DONE)
                .generatedBio("draft").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(jobs.findByIdAndConcertId(jobId, concert.getId())).thenReturn(Optional.of(job));
        assertThat(service.get(actor, concert.getId(), jobId).generatedBio()).isEqualTo("draft");
    }
}
