package com.ticketbox.api.infrastructure.rateLimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketbox.api.module.auth.config.AuthRateLimitPolicy;
import com.ticketbox.api.module.order.config.OrderRateLimitPolicy;
import com.ticketbox.api.module.payment.config.PaymentRateLimitPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitPolicyRegistryTest {
    private final RateLimitPolicyRegistry registry = new RateLimitPolicyRegistry(List.of(
            new AuthRateLimitPolicy(), new OrderRateLimitPolicy(), new PaymentRateLimitPolicy()));

    @Test
    void selectsStableResourcesWithoutUsingRouteIds() {
        assertEquals("order:create", registry.find("POST", "/orders").orElseThrow().resource());
        assertEquals("order:read", registry.find("GET", "/orders/first-id").orElseThrow().resource());
        assertEquals("order:read", registry.find("GET", "/orders/second-id").orElseThrow().resource());
        assertEquals("payment:initiate", registry.find("POST", "/payments").orElseThrow().resource());
        assertEquals("payment:initiate", registry.find("POST", "/orders/some-id/payments").orElseThrow().resource());
        assertEquals("payment:read", registry.find("GET", "/payments/some-id").orElseThrow().resource());
        assertEquals("auth:me", registry.find("GET", "/auth/me").orElseThrow().resource());
        assertEquals("auth:logout", registry.find("POST", "/auth/logout").orElseThrow().resource());
    }

    @Test
    void excludesPublicAndProviderRoutes() {
        assertTrue(registry.find("GET", "/concerts").isEmpty());
        assertTrue(registry.find("GET", "/payments/vnpay/ipn").isEmpty());
        assertTrue(registry.find("POST", "/payments/momo/ipn").isEmpty());
        assertTrue(registry.find("POST", "/auth/refresh").isEmpty());
        assertTrue(registry.find("POST", "/auth/login").isEmpty());
    }
}
