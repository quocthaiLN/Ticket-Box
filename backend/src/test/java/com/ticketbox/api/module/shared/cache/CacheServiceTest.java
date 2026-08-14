package com.ticketbox.api.module.shared.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SingleFlight singleFlight;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        singleFlight = new SingleFlight();
        cacheService = new CacheService(stringRedisTemplate, objectMapper, singleFlight);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should return cached value on Cache HIT without calling DB supplier")
    void getOrFetch_CacheHit() {
        when(valueOperations.get("concerts:123")).thenReturn("{\"id\":\"123\",\"title\":\"Cached Concert\"}");
        AtomicInteger dbCalls = new AtomicInteger(0);

        TestConcertDto result = cacheService.getOrFetch("concerts:123", Duration.ofMinutes(30), TestConcertDto.class, () -> {
            dbCalls.incrementAndGet();
            return new TestConcertDto("123", "DB Concert");
        });

        assertNotNull(result);
        assertEquals("123", result.getId());
        assertEquals("Cached Concert", result.getTitle());
        assertEquals(0, dbCalls.get());
    }

    @Test
    @DisplayName("Should query DB supplier and set Redis cache on Cache MISS")
    void getOrFetch_CacheMiss() {
        when(valueOperations.get("concerts:456")).thenReturn(null);
        AtomicInteger dbCalls = new AtomicInteger(0);

        TestConcertDto result = cacheService.getOrFetch("concerts:456", Duration.ofMinutes(30), TestConcertDto.class, () -> {
            dbCalls.incrementAndGet();
            return new TestConcertDto("456", "Fresh Concert");
        });

        assertNotNull(result);
        assertEquals("456", result.getId());
        assertEquals("Fresh Concert", result.getTitle());
        assertEquals(1, dbCalls.get());
        verify(valueOperations, times(1)).set(eq("concerts:456"), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Should generate deterministic MD5 hash key for filter map")
    void generateHashKey_deterministic() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("q", "rock");
        params1.put("city", "Hanoi");
        params1.put("page", 0);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("city", "Hanoi");
        params2.put("page", 0);
        params2.put("q", "rock");

        String key1 = cacheService.generateHashKey("concerts:all", params1);
        String key2 = cacheService.generateHashKey("concerts:all", params2);

        assertNotNull(key1);
        assertTrue(key1.startsWith("concerts:all:"));
        assertEquals(key1, key2, "Keys should be identical regardless of map insertion order");
    }

    public static class TestConcertDto {
        private String id;
        private String title;

        public TestConcertDto() {}
        public TestConcertDto(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
