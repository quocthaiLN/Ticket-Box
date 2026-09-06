package com.ticketbox.api.module.catalog.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertDetailResponse;
import com.ticketbox.api.module.catalog.domain.dtos.ConcertResponse;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.shared.cache.CacheService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatZoneRepository seatZoneRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private PublicConcertServiceImpl publicConcertService;

    private Concert publishedConcert;
    private Concert draftConcert;
    private UUID publishedConcertId;
    private UUID draftConcertId;

    @BeforeEach
    void setUp() {
        lenient().when(cacheService.getOrFetch(anyString(), any(), any(Class.class), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        });
        lenient().when(cacheService.getOrFetch(anyString(), any(), any(TypeReference.class), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        });
        lenient().when(cacheService.generateHashKey(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0) + ":hash");

        publishedConcertId = UUID.randomUUID();
        publishedConcert = Concert.builder()
                .id(publishedConcertId)
                .title("Published Concert")
                .slug("published-concert")
                .venue("My Dinh")
                .artistName("Artist A")
                .startsAt(LocalDateTime.now().plusDays(10))
                .endsAt(LocalDateTime.now().plusDays(10).plusHours(3))
                .status(ConcertStatus.PUBLISHED)
                .build();

        draftConcertId = UUID.randomUUID();
        draftConcert = Concert.builder()
                .id(draftConcertId)
                .title("Draft Concert")
                .slug("draft-concert")
                .venue("Phu Tho")
                .artistName("Artist B")
                .startsAt(LocalDateTime.now().plusDays(15))
                .endsAt(LocalDateTime.now().plusDays(15).plusHours(3))
                .status(ConcertStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("Get published concert detail successfully")
    void getConcertDetail_success() {
        when(concertRepository.findById(publishedConcertId)).thenReturn(Optional.of(publishedConcert));

        ConcertDetailResponse detail = publicConcertService.getConcertDetail(publishedConcertId);

        assertNotNull(detail);
        assertEquals(publishedConcertId, detail.getId());
        assertEquals("Published Concert", detail.getTitle());
        assertEquals("PUBLISHED", detail.getStatus());
    }

    @Test
    @DisplayName("Getting draft concert via public service throws NOT_FOUND")
    void getConcertDetail_draftConcert_throwsNotFound() {
        when(concertRepository.findById(draftConcertId)).thenReturn(Optional.of(draftConcert));

        AppException ex = assertThrows(AppException.class, () ->
                publicConcertService.getConcertDetail(draftConcertId)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("CONCERT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("List published concerts returns paginated response")
    void getPublishedConcerts_success() {
        PageImpl<Concert> page = new PageImpl<>(List.of(publishedConcert));
        when(concertRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<ConcertResponse> result = publicConcertService.getPublishedConcerts(
                "Published", null, null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Published Concert", result.getContent().get(0).getTitle());
    }
}
