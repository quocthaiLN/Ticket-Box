package com.ticketbox.api.module.catalog.schedulers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertWarmUpMessage;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogWarmUpScheduler {

    private static final String LOCK_KEY = "lock:catalog:cache-warmup";
    private static final Duration LOCK_EXPIRATION = Duration.ofSeconds(25);

    private final ConcertRepository concertRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 30000)
    public void scanAndTriggerCacheWarmUp() {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, LOCK_EXPIRATION);

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Catalog warm-up scheduler lock not acquired by this instance. Skipping run.");
            return;
        }

        try {
            log.info("Catalog warm-up scheduler running under lock token: {}", lockToken);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusMinutes(3);

            List<Concert> upcomingConcerts = concertRepository.findByStatusAndStartsAtBetween(
                    Concert.ConcertStatus.PUBLISHED,
                    now,
                    windowEnd
            );

            if (upcomingConcerts.isEmpty()) {
                log.debug("No upcoming published concerts found within T-3 minutes window.");
                return;
            }

            for (Concert concert : upcomingConcerts) {
                ConcertWarmUpMessage message = ConcertWarmUpMessage.builder()
                        .concertId(concert.getId())
                        .startsAt(concert.getStartsAt())
                        .build();

                rabbitTemplate.convertAndSend(
                        RabbitMqConstants.CATALOG_EXCHANGE,
                        RabbitMqConstants.CATALOG_CACHE_WARMUP_ROUTING_KEY,
                        message
                );

                log.info("Published cache warm-up request for concertId: {}, startsAt: {}", concert.getId(), concert.getStartsAt());
            }
        } catch (Exception e) {
            log.error("Error executing catalog warm-up scheduler: {}", e.getMessage(), e);
        } finally {
            try {
                String currentLock = stringRedisTemplate.opsForValue().get(LOCK_KEY);
                if (lockToken.equals(currentLock)) {
                    stringRedisTemplate.delete(LOCK_KEY);
                }
            } catch (Exception e) {
                log.warn("Failed to release lock key {}: {}", LOCK_KEY, e.getMessage());
            }
        }
    }
}
