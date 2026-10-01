package com.ticketbox.api.module.catalog.config;

import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicy;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicyProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CatalogAdminRateLimitPolicy implements RateLimitPolicyProvider {
    @Override
    public List<RateLimitPolicy> policies() {
        return List.of(
                RateLimitPolicy.of("GET", "^/admin/concerts$", "catalog-admin:read", 60, 60_000, false),
                RateLimitPolicy.of("POST", "^/admin/concerts$", "catalog-admin:write", 60, 60_000, true),
                RateLimitPolicy.of("PATCH", "^/admin/concerts/[^/]+$", "catalog-admin:write", 60, 60_000, true),
                RateLimitPolicy.of("POST", "^/admin/concerts/[^/]+/(publish|cancel|seat-zones|ticket-types)$", "catalog-admin:write", 60, 60_000, true),
                RateLimitPolicy.of("PATCH", "^/admin/seat-zones/[^/]+$", "catalog-admin:write", 60, 60_000, true),
                RateLimitPolicy.of("PATCH", "^/admin/ticket-types/[^/]+$", "catalog-admin:write", 60, 60_000, true)
        );
    }
}
