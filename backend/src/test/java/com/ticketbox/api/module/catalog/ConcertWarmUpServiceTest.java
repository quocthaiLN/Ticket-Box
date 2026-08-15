package com.ticketbox.api.module.catalog;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertDetailResponse;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertMetadataResponse;
import com.ticketbox.api.module.catalog.domain.dtos.InventoryResponse;
import com.ticketbox.api.module.catalog.domain.dtos.SeatMapResponse;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.catalog.services.ConcertWarmUpServiceImpl;
import com.ticketbox.api.module.shared.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertWarmUpServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatZoneRepository seatZoneRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private ConcertWarmUpServiceImpl warmUpService;

    private UUID concertId;
    private Concert concert;
    private SeatZone seatZone;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        concertId = UUID.randomUUID();
        User organizer = User.builder().id(UUID.randomUUID()).fullName("Organizer Test").build();

        concert = Concert.builder()
                .id(concertId)
                .organizer(organizer)
                .title("Test Concert WarmUp")
                .slug("test-concert-warmup")
                .venue("Test Venue")
                .artistName("Test Artist")
                .startsAt(LocalDateTime.now().plusMinutes(2))
                .endsAt(LocalDateTime.now().plusHours(2))
                .status(Concert.ConcertStatus.PUBLISHED)
                .build();

        seatZone = SeatZone.builder()
                .id(UUID.randomUUID())
                .concert(concert)
                .code("ZONE_A")
                .name("Zone A")
                .capacity(100)
                .build();

        ticketType = TicketType.builder()
                .id(UUID.randomUUID())
                .concert(concert)
                .seatZone(seatZone)
                .name("VIP Ticket")
                .price(BigDecimal.valueOf(1000000))
                .currency("VND")
                .totalQuantity(100)
                .heldQuantity(0)
                .soldQuantity(10)
                .maxPerUser(4)
                .status(TicketType.TicketTypeStatus.ACTIVE)
                .build();
    }

    @Test
    void warmUpConcertCache_WhenConcertFound_ShouldPopulateAllRedisKeys() {
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));
        when(seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId)).thenReturn(List.of(seatZone));
        when(ticketTypeRepository.findByConcertId(concertId)).thenReturn(List.of(ticketType));
        when(cacheService.generateHashKey(anyString(), anyMap())).thenReturn("concerts:" + concertId + ":ticket-types:hash");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        warmUpService.warmUpConcertCache(concertId);

        // Verify cache keys populated
        verify(cacheService, times(1)).set(eq("concerts:" + concertId), any(ConcertDetailResponse.class), eq(Duration.ofMinutes(30)));
        verify(cacheService, times(1)).set(eq("concerts:" + concertId + ":metadata"), any(ConcertMetadataResponse.class), eq(Duration.ofHours(24)));
        verify(cacheService, times(1)).set(eq("concerts:" + concertId + ":seatmap"), any(SeatMapResponse.class), eq(Duration.ofHours(1)));
        verify(cacheService, times(1)).set(eq("concerts:" + concertId + ":ticket-types:hash"), anyList(), eq(Duration.ofMinutes(30)));
        verify(cacheService, times(1)).set(eq("concerts:" + concertId + ":inventory"), any(InventoryResponse.class), eq(Duration.ofMinutes(5)));

        // Verify inventory hash snapshot
        verify(hashOperations, times(1)).put(eq("inventory:concert:" + concertId), eq(ticketType.getId().toString()), eq("90"));
    }

    @Test
    void warmUpConcertCache_WhenConcertNotFound_ShouldDoNothing() {
        when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

        warmUpService.warmUpConcertCache(concertId);

        verifyNoInteractions(cacheService);
        verifyNoInteractions(stringRedisTemplate);
    }
}
