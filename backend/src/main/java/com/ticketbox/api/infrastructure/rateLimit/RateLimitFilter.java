package com.ticketbox.api.infrastructure.rateLimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.response.ErrorResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;


@AllArgsConstructor 
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitPolicyRegistry policies;
    private final RedisRateLimiter limiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<RateLimitPolicy> match = policies.find(request.getMethod(), path);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (match.isEmpty() || authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitPolicy policy = match.orElseThrow();
        long start = System.nanoTime();
        RedisRateLimiter.Decision decision;
        try {
            decision = limiter.check(principal.getUser().getId(), policy);
        } catch (RuntimeException exception) {
            log.error("rate_limit outcome=redis_error resource={} latency_ms={}", policy.resource(),
                    (System.nanoTime() - start) / 1_000_000, exception);
            if (policy.failClosed()) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "RATE_LIMIT_UNAVAILABLE", "Rate limiting is temporarily unavailable");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (!decision.allowed()) {
            log.info("rate_limit outcome=rejected resource={} latency_ms={}", policy.resource(),
                    (System.nanoTime() - start) / 1_000_000);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (decision.retryAfterMs() + 999) / 1000)));
            writeError(response, 429, "RATE_LIMITED", "Too many requests");
            return;
        }

        log.debug("rate_limit outcome=allowed resource={} latency_ms={}", policy.resource(),
                (System.nanoTime() - start) / 1_000_000);
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
