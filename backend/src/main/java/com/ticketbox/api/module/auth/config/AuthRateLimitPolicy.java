package com.ticketbox.api.module.auth.config;

import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicy;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicyProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimitPolicy implements RateLimitPolicyProvider {
    @Override
    public List<RateLimitPolicy> policies() {
        return List.of(
                RateLimitPolicy.of("GET", "^/auth/me$", "auth:me", 60, 60_000, false),
                RateLimitPolicy.of("POST", "^/auth/logout$", "auth:logout", 10, 60_000, false)
        );
    }
}
