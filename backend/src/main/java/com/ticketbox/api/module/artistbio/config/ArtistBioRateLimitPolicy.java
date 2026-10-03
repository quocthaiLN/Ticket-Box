package com.ticketbox.api.module.artistbio.config;

import com.ticketbox.api.infrastructure.rateLimit.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ArtistBioRateLimitPolicy implements RateLimitPolicyProvider {
    @Override public List<RateLimitPolicy> policies() {
        return List.of(
                RateLimitPolicy.of("POST", "^/admin/concerts/[^/]+/artist-bio-jobs$", "artist-bio:upload", 10, 60_000, true),
                RateLimitPolicy.of("GET", "^/admin/concerts/[^/]+/artist-bio-jobs/[^/]+$", "artist-bio:poll", 60, 60_000, false));
    }
}
