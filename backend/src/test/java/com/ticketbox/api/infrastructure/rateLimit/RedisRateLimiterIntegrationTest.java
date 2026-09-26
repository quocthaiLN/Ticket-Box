package com.ticketbox.api.infrastructure.rateLimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimiterIntegrationTest {
    @Test
    void enforcesSlidingWindowWithRedisTimeAndExpiresLog() throws Exception {
        int port = testPort();
        LettuceConnectionFactory connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            RedisRateLimiter limiter = new RedisRateLimiter(redis);
            UUID userId = UUID.randomUUID();
            RateLimitPolicy policy = RateLimitPolicy.of("POST", "^/orders$", "order:create", 2, 500, true);
            String key = "ratelimit:user:" + userId + ":order:create";

            assertTrue(limiter.check(userId, policy).allowed());
            assertTrue(limiter.check(userId, policy).allowed());
            assertEquals(2L, redis.opsForZSet().size(key));
            RedisRateLimiter.Decision rejected = limiter.check(userId, policy);
            assertFalse(rejected.allowed());
            assertTrue(rejected.retryAfterMs() > 0 && rejected.retryAfterMs() <= 500);
            assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) > 0);

            Thread.sleep(550);
            assertTrue(limiter.check(userId, policy).allowed());
            assertEquals(1L, redis.opsForZSet().size(key));
        } finally {
            connection.destroy();
        }
    }

    @Test
    void concurrentRequestsCannotExceedQuota() throws Exception {
        int port = testPort();
        LettuceConnectionFactory connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        try (var pool = Executors.newFixedThreadPool(8)) {
            RedisRateLimiter limiter = new RedisRateLimiter(new StringRedisTemplate(connection));
            UUID userId = UUID.randomUUID();
            RateLimitPolicy policy = RateLimitPolicy.of("POST", "^/orders$", "order:create", 1, 5_000, true);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                start.await();
                return limiter.check(userId, policy).allowed();
            };
            var results = List.of(pool.submit(attempt), pool.submit(attempt), pool.submit(attempt),
                    pool.submit(attempt), pool.submit(attempt), pool.submit(attempt),
                    pool.submit(attempt), pool.submit(attempt));
            start.countDown();
            int allowed = 0;
            for (var result : results) {
                if (result.get()) allowed++;
            }
            assertEquals(1, allowed);
        } finally {
            connection.destroy();
        }
    }

    private int testPort() {
        String value = System.getenv("REDIS_TEST_PORT");
        Assumptions.assumeTrue(value != null, "Set REDIS_TEST_PORT for Redis integration tests");
        return Integer.parseInt(value);
    }
}
