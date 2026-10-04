package com.ticketbox.api.module.artistbio.messaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.ticketbox.api.module.artistbio.ai.ArtistBioGenerator;
import com.ticketbox.api.module.artistbio.ai.PdfTextExtractor;
import com.ticketbox.api.module.artistbio.repositories.ArtistBioJobRepository;
import com.ticketbox.api.module.artistbio.services.ArtistBioJobStateService;
import com.ticketbox.api.module.artistbio.services.ArtistBioProcessor;
import com.ticketbox.api.module.artistbio.services.ArtistBioRecoveryScheduler;
import com.ticketbox.api.module.artistbio.storage.ArtistBioStorage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ArtistBioProfileTest {
    @ParameterizedTest
    @ValueSource(strings = {"api", "worker"})
    void processingPipelineIsRegisteredForActiveBackendProfile(String profile) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.registerBean(ArtistBioJobStateService.class, () -> mock(ArtistBioJobStateService.class));
            context.registerBean(ArtistBioStorage.class, () -> mock(ArtistBioStorage.class));
            context.registerBean(PdfTextExtractor.class, () -> mock(PdfTextExtractor.class));
            context.registerBean(ArtistBioGenerator.class, () -> mock(ArtistBioGenerator.class));
            context.registerBean(ArtistBioJobRepository.class, () -> mock(ArtistBioJobRepository.class));
            context.registerBean(ArtistBioProducer.class, () -> mock(ArtistBioProducer.class));
            context.register(ArtistBioConsumer.class, ArtistBioProcessor.class, ArtistBioRecoveryScheduler.class);
            context.refresh();

            assertNotNull(context.getBean(ArtistBioConsumer.class));
            assertNotNull(context.getBean(ArtistBioProcessor.class));
            assertNotNull(context.getBean(ArtistBioRecoveryScheduler.class));
        }
    }
}
