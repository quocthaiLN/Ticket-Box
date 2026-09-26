package com.ticketbox.api.infrastructure.rateLimit;

import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("rate-limit/sliding-window.lua"));
        this.script.setResultType(List.class);
    }

    public Decision check(UUID userId, RateLimitPolicy policy) {
        String key = "ratelimit:user:" + userId + ":" + policy.resource();
        List<?> result = redis.execute(script, List.of(key),
                Integer.toString(policy.limit()), Long.toString(policy.windowMs()), UUID.randomUUID().toString());
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Invalid rate limit result from Redis");
        }
        return new Decision(number(result.get(0)) == 1, number(result.get(1)), number(result.get(2)));
    }

    private static long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Invalid rate limit result from Redis");
        }
        return number.longValue();
    }

    public record Decision(boolean allowed, long remaining, long retryAfterMs) {}
}
