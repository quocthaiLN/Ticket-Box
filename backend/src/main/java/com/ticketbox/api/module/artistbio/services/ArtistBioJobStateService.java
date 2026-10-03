package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.domain.entities.*;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArtistBioJobStateService {
    public static final int MAX_ATTEMPTS = 3;
    public static final int RETRY_SECONDS = 30;
    public static final int LEASE_SECONDS = 300;
    private final ArtistBioJobRepository jobs;

    // Trạng thái của Message
    public enum Outcome { SKIP, PROCESS, RETRY, DEAD, DONE }
    // Kết quả xin xử lý
    public record Claim(Outcome outcome, UUID token, String source) {}

    // Khóa bản ghi Job trong DB và kiểm tra trạng thái và gắn Outcome -> trả về Claim
    @Transactional
    public Claim claim(UUID id) {
        ArtistBioJob job = jobs.findForUpdate(id).orElse(null);
        if (job == null || job.getStatus() == ArtistBioJobStatus.DONE) return new Claim(Outcome.SKIP, null, null);
        if (job.getStatus() == ArtistBioJobStatus.FAILED) return new Claim(Outcome.DEAD, null, null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (job.getStatus() == ArtistBioJobStatus.PENDING && job.getNextAttemptAt() != null
                && job.getNextAttemptAt().isAfter(now)) return new Claim(Outcome.SKIP, null, null);
        if (job.getStatus() == ArtistBioJobStatus.PROCESSING && job.getLeaseUntil() != null
                && job.getLeaseUntil().isAfter(now)) return new Claim(Outcome.SKIP, null, null);
        if (job.getAttempts() >= MAX_ATTEMPTS) {
            finish(job, ArtistBioJobStatus.FAILED, "Processing attempts exhausted");
            return new Claim(Outcome.DEAD, null, null);
        }
        UUID token = UUID.randomUUID();
        job.setStatus(ArtistBioJobStatus.PROCESSING);
        job.setAttempts(job.getAttempts() + 1);
        job.setProcessingToken(token);
        job.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        job.setNextAttemptAt(null);
        job.setErrorMessage(null);
        return new Claim(Outcome.PROCESS, token, job.getSourceFileUrl());
    }

    // Xác nhận Message sau xử lý thành công
    @Transactional
    public Outcome complete(UUID id, UUID token, String text, String bio) {
        ArtistBioJob job = jobs.findForUpdate(id).orElse(null);
        if (!owns(job, token)) return Outcome.SKIP;
        job.setExtractedText(text);
        job.setGeneratedBio(bio);
        finish(job, ArtistBioJobStatus.DONE, null);
        return Outcome.DONE;
    }

    // Xác nhận Message sau xử lý thất bại -> về DLX hoặc Retry Queue
    @Transactional
    public Outcome fail(UUID id, UUID token, String message, boolean retryable) {
        ArtistBioJob job = jobs.findForUpdate(id).orElse(null);
        if (!owns(job, token)) return Outcome.SKIP;
        boolean retry = retryable && job.getAttempts() < MAX_ATTEMPTS;
        finish(job, retry ? ArtistBioJobStatus.PENDING : ArtistBioJobStatus.FAILED, message);
        if (retry) job.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(RETRY_SECONDS));
        return retry ? Outcome.RETRY : Outcome.DEAD;
    }

    // Kiểm tra worker đc xử lý hay không 
    private boolean owns(ArtistBioJob job, UUID token) {
        return job != null && token != null && job.getStatus() == ArtistBioJobStatus.PROCESSING
                && token.equals(job.getProcessingToken()) && job.getLeaseUntil() != null
                && job.getLeaseUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    
    private void finish(ArtistBioJob job, ArtistBioJobStatus status, String error) {
        job.setStatus(status);
        job.setErrorMessage(error);
        job.setProcessingToken(null);
        job.setLeaseUntil(null);
        job.setNextAttemptAt(null);
    }
}
