package com.ticketbox.api.module.artistbio.services;

import com.ticketbox.api.module.artistbio.messaging.ArtistBioProducer;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"api", "worker"})
@RequiredArgsConstructor
@Slf4j
public class ArtistBioRecoveryScheduler {
    private final ArtistBioJobRepository jobs;
    private final ArtistBioProducer producer;

    // Duyệt DB tìm Job đã quá lâu chưa xử lý
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void recover() {
        for (var id : jobs.findRecoverable(LocalDateTime.now(ZoneOffset.UTC), PageRequest.of(0, 100))) {
            try { producer.generate(id); }
            catch (RuntimeException exception) {
                log.warn("Artist bio recovery publish failed for {}; will retry next scan", id);
                break; // Do not block the shared scheduler with 100 broker timeouts.
            }
        }
    }
}
