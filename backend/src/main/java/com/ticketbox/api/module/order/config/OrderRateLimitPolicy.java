package com.ticketbox.api.module.order.config;

import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicy;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicyProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderRateLimitPolicy implements RateLimitPolicyProvider {
    @Override
    public List<RateLimitPolicy> policies() {
        return List.of(
                RateLimitPolicy.of("POST", "^/orders$", "order:create", 6, 60_000, true),
                RateLimitPolicy.of("GET", "^/orders/[^/]+$", "order:read", 60, 60_000, false)
        );
    }
}
