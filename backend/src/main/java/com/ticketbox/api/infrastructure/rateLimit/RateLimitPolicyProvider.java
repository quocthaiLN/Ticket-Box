package com.ticketbox.api.infrastructure.rateLimit;

import java.util.List;

public interface RateLimitPolicyProvider {
    List<RateLimitPolicy> policies();
}
