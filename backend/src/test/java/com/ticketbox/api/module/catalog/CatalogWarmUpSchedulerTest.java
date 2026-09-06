package com.ticketbox.api.module.catalog;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertWarmUpMessage;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.schedulers.CatalogWarmUpScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogWarmUpSchedulerTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CatalogWarmUpScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void scanAndTriggerCacheWarmUp_WhenLockNotAcquired_ShouldDoNothing() {
        when(valueOperations.setIfAbsent(eq("lock:catalog:cache-warmup"), anyString(), any(Duration.class)))
                .thenReturn(false);

        scheduler.scanAndTriggerCacheWarmUp();

        verifyNoInteractions(concertRepository);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void scanAndTriggerCacheWarmUp_WhenLockAcquired_ShouldQueryAndPublishMessages() {
        when(valueOperations.setIfAbsent(eq("lock:catalog:cache-warmup"), anyString(), any(Duration.class)))
                .thenReturn(true);

        UUID concertId = UUID.randomUUID();
        LocalDateTime startsAt = LocalDateTime.now().plusMinutes(2);
        Concert concert = Concert.builder()
                .id(concertId)
                .startsAt(startsAt)
                .status(ConcertStatus.PUBLISHED)
                .build();

        when(concertRepository.findByStatusAndStartsAtBetween(eq(ConcertStatus.PUBLISHED), any(), any()))
                .thenReturn(List.of(concert));
        when(valueOperations.get("lock:catalog:cache-warmup")).thenReturn("lock-token");

        scheduler.scanAndTriggerCacheWarmUp();

        ArgumentCaptor<ConcertWarmUpMessage> messageCaptor = ArgumentCaptor.forClass(ConcertWarmUpMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConstants.CATALOG_EXCHANGE),
                eq(RabbitMqConstants.CATALOG_CACHE_WARMUP_ROUTING_KEY),
                messageCaptor.capture()
        );

        assertEquals(concertId, messageCaptor.getValue().getConcertId());
        assertEquals(startsAt, messageCaptor.getValue().getStartsAt());
    }
}
