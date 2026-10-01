package com.ticketbox.api.module.shared.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SingleFlight singleFlight;

    public <T> T getOrFetch(String cacheKey, Duration ttl, Class<T> clazz, Supplier<T> dbSupplier) {
        T cached = get(cacheKey, clazz);
        if (cached != null) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return cached;
        }

        return singleFlight.execute(cacheKey, () -> {
            T recheck = get(cacheKey, clazz);
            if (recheck != null) {
                log.debug("Cache HIT (double-check) for key: {}", cacheKey);
                return recheck;
            }

            log.debug("Cache MISS for key: {}. Fetching from DB.", cacheKey);
            T data = dbSupplier.get();
            if (data != null) {
                set(cacheKey, data, ttl);
            }
            return data;
        });
    }

    public <T> T getOrFetch(String cacheKey, Duration ttl, TypeReference<T> typeRef, Supplier<T> dbSupplier) {
        T cached = get(cacheKey, typeRef);
        if (cached != null) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return cached;
        }

        return singleFlight.execute(cacheKey, () -> {
            T recheck = get(cacheKey, typeRef);
            if (recheck != null) {
                log.debug("Cache HIT (double-check) for key: {}", cacheKey);
                return recheck;
            }

            log.debug("Cache MISS for key: {}. Fetching from DB.", cacheKey);
            T data = dbSupplier.get();
            if (data != null) {
                set(cacheKey, data, ttl);
            }
            return data;
        });
    }

    public <T> T get(String cacheKey, Class<T> clazz) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, clazz);
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache key {}: {}", cacheKey, e.getMessage());
        }
        return null;
    }

    public <T> T get(String cacheKey, TypeReference<T> typeRef) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, typeRef);
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache key {}: {}", cacheKey, e.getMessage());
        }
        return null;
    }

    public void set(String cacheKey, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (Exception e) {
            log.warn("Failed to write to cache key {}: {}", cacheKey, e.getMessage());
        }
    }

    public String generateHashKey(String prefix, Map<String, Object> params) {
        try {
            StringBuilder sb = new StringBuilder();
            params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        if (e.getValue() != null) {
                            sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
                        }
                    });
            if (sb.length() == 0) {
                return prefix + ":default";
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return prefix + ":" + hexString.substring(0, 16);
        } catch (Exception e) {
            return prefix + ":" + Math.abs(params.hashCode());
        }
    }

    public void evictConcertCache(UUID concertId) {
        try {
            Set<String> keys = stringRedisTemplate.keys("concerts:" + concertId + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("Evicted {} cache keys matching concerts:{}:*", keys.size(), concertId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache for concertId {}: {}", concertId, e.getMessage());
        }
    }

    public void evictAllConcertsCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys("concerts:all*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("Evicted {} cache keys matching concerts:all*", keys.size());
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache for concerts:all*: {}", e.getMessage());
        }
    }

    /** Strict variant for after-commit admin writes so bounded retries can observe Redis failures. */
    public void invalidateAdminConcert(UUID concertId) {
        Set<String> concertKeys = stringRedisTemplate.keys("concerts:" + concertId + "*");
        Set<String> listKeys = stringRedisTemplate.keys("concerts:all*");
        if (concertKeys != null && !concertKeys.isEmpty()) stringRedisTemplate.delete(concertKeys);
        if (listKeys != null && !listKeys.isEmpty()) stringRedisTemplate.delete(listKeys);
    }
}
