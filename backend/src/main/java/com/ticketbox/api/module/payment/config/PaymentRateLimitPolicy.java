package com.ticketbox.api.module.payment.config;

import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicy;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicyProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentRateLimitPolicy implements RateLimitPolicyProvider {
    @Override
    public List<RateLimitPolicy> policies() {
        return List.of(
                RateLimitPolicy.of("POST", "^/payments$", "payment:initiate", 6, 60_000, true),
                RateLimitPolicy.of("POST", "^/orders/[^/]+/payments$", "payment:initiate", 6, 60_000, true),
                RateLimitPolicy.of("GET", "^/payments/[^/]+$", "payment:read", 60, 60_000, false)
        );
    }
}
