package com.ticketbox.api.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklistToken(String jti, long ttlMillis) {
        if (jti == null || jti.isBlank() || ttlMillis <= 0) {
            return;
        }
        try {
            String key = BLACKLIST_KEY_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "revoked", ttlMillis, TimeUnit.MILLISECONDS);
            log.info("Token jti [{}] blacklisted for {} ms", jti, ttlMillis);
        } catch (Exception e) {
            log.error("Failed to blacklist token jti [{}] in Redis: {}", jti, e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            String key = BLACKLIST_KEY_PREFIX + jti;
            Boolean hasKey = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.error("Failed to check token blacklist status in Redis for jti [{}]: {}", jti, e.getMessage());
            return false;
        }
    }
}
