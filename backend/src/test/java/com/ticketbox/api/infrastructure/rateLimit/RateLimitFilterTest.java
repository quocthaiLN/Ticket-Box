package com.ticketbox.api.infrastructure.rateLimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.auth.config.AuthRateLimitPolicy;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.order.config.OrderRateLimitPolicy;
import com.ticketbox.api.module.payment.config.PaymentRateLimitPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {
    private final UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final RedisRateLimiter limiter = mock(RedisRateLimiter.class);
    private final RateLimitPolicyRegistry registry = new RateLimitPolicyRegistry(List.of(
            new AuthRateLimitPolicy(), new OrderRateLimitPolicy(), new PaymentRateLimitPolicy()));
    private final RateLimitFilter filter = new RateLimitFilter(registry, limiter, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsCheckoutBeforeControllerWithRoundedRetryAfter() throws Exception {
        authenticate();
        MockHttpServletRequest request = request("POST", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check(eq(userId), any())).thenReturn(new RedisRateLimiter.Decision(false, 0, 1001));

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("2", response.getHeader("Retry-After"));
        assertEquals("RATE_LIMITED", new ObjectMapper().readTree(response.getContentAsString())
                .path("error").path("code").asText());
        assertNull(chain.getRequest());
        verify(limiter).check(eq(userId), any());
    }

    @Test
    void ignoresUntrustedUserIdHeaderAndUnauthenticatedRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/orders");
        request.addHeader("X-User-ID", userId.toString());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(request, chain.getRequest());
        verifyNoInteractions(limiter);
    }

    @Test
    void failsClosedForPaymentCreationWhenRedisFails() throws Exception {
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check(eq(userId), any())).thenThrow(new IllegalStateException("Redis down"));

        filter.doFilter(request("POST", "/orders/abc/payments"), response, chain);

        assertEquals(503, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void failsOpenForReadWhenRedisFails() throws Exception {
        authenticate();
        MockHttpServletRequest request = request("GET", "/payments/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check(eq(userId), any())).thenThrow(new IllegalStateException("Redis down"));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }

    private void authenticate() {
        User user = User.builder().id(userId).build();
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
