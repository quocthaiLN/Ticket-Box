package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.domain.dtos.ArtistBioJobResponse;
import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJob;
import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioJobNotFoundException;
import com.ticketbox.api.module.artistbio.domain.mappers.ArtistBioMapper;
import com.ticketbox.api.module.artistbio.messaging.ArtistBioProducer;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import com.ticketbox.api.module.artistbio.storage.ArtistBioStorage;
import com.ticketbox.api.module.artistbio.storage.ArtistBioUpload;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.services.ConcertService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArtistBioServiceImpl implements ArtistBioService {
    private final ConcertService concertService;
    private final ArtistBioJobRepository jobs;
    private final ArtistBioStorage storage;
    private final ArtistBioProducer producer;
    private final AuditLogService audit;
    private final TransactionTemplate transactionTemplate;
    private final ArtistBioMapper artistBioMapper;

    @Override
    public ArtistBioJobResponse upload(User actor, UUID concertId, MultipartFile file) {
        concertService.getConcertForArtistBioUpload(actor, concertId);
        byte[] bytes = ArtistBioUpload.read(file);
        String source = storage.upload(concertId, UUID.randomUUID(), bytes);
        var cleanupRegistered = new AtomicBoolean();
        try {
            return transactionTemplate.execute(status -> createJob(actor, concertId, source, cleanupRegistered));
        } catch (RuntimeException exception) {
            if (!cleanupRegistered.get()) storage.delete(source);
            throw exception;
        }
    }

    private ArtistBioJobResponse createJob(User actor, UUID concertId, String source,
            AtomicBoolean cleanupRegistered) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int completion) {
                if (completion == STATUS_ROLLED_BACK) storage.delete(source);
            }
        });
        cleanupRegistered.set(true);
        Concert concert = concertService.getConcertForArtistBioUploadForUpdate(actor, concertId);
        ArtistBioJob job = jobs.saveAndFlush(ArtistBioJob.builder()
                .concert(concert).requestedBy(actor).sourceFileUrl(source).build());
        audit.logBusinessSuccess(AuditAction.CREATE_ARTIST_BIO_JOB,
                "ARTIST_BIO_JOB", job.getId().toString(),
                Map.of("concert_id", concertId.toString()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    producer.generate(job.getId());
                } catch (RuntimeException exception) {
                    log.warn("Artist bio job {} awaits redispatch", job.getId());
                }
            }
        });
        return artistBioMapper.toArtistBioJobResponse(job);
    }

    @Override
    public ArtistBioJobResponse get(User actor, UUID concertId, UUID jobId) {
        concertService.checkArtistBioAccess(actor, concertId);
        return transactionTemplate.execute(status -> artistBioMapper.toArtistBioJobResponse(
                jobs.findByIdAndConcertId(jobId, concertId).orElseThrow(ArtistBioJobNotFoundException::new)));
    }
}
