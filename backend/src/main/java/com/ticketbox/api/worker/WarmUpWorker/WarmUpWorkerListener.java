package com.ticketbox.api.worker.WarmUpWorker;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertWarmUpMessage;
import com.ticketbox.api.module.catalog.services.ConcertWarmUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmUpWorkerListener {

    private final ConcertWarmUpService concertWarmUpService;

    @RabbitListener(queues = RabbitMqConstants.CATALOG_CACHE_WARMUP_QUEUE)
    public void onConcertWarmUpMessage(ConcertWarmUpMessage message) {
        if (message == null || message.getConcertId() == null) {
            log.warn("Received empty or invalid ConcertWarmUpMessage");
            return;
        }

        log.info("Worker received cache warm-up task for concertId: {}", message.getConcertId());
        try {
            concertWarmUpService.warmUpConcertCache(message.getConcertId());
        } catch (Exception e) {
            log.error("Failed to process cache warm-up for concertId {}: {}", message.getConcertId(), e.getMessage(), e);
        }
    }
}
