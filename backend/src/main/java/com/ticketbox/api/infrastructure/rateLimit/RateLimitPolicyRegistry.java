package com.ticketbox.api.infrastructure.rateLimit;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RateLimitPolicyRegistry {
    private final List<RateLimitPolicy> policies;

    public RateLimitPolicyRegistry(List<RateLimitPolicyProvider> providers) {
        this.policies = providers.stream().flatMap(provider -> provider.policies().stream()).toList();
    }

    public Optional<RateLimitPolicy> find(String method, String path) {
        return policies.stream().filter(policy -> policy.matches(method, path)).findFirst();
    }
}
