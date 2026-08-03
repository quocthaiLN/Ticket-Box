package com.ticketbox.api.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "accessTokenSecret", "fbf31e0e58fa369c5ad1909f9d013ee03fb267e767aedff8b2fff6cc9b6dc9f6");
        ReflectionTestUtils.setField(jwtUtils, "accessTokenExpirationRaw", "900000");
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenSecret", "ccd0c7a7b168a1fe1a719e0afa3ad66b0a2afb6fba8bf0f877faa1e855349a66");
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenExpirationRaw", "2592000000");
        jwtUtils.init();
    }

    @Test
    void testParseExpirationDirectNumeric() {
        Long accessExp = (Long) ReflectionTestUtils.getField(jwtUtils, "accessTokenExpiration");
        Long refreshExp = (Long) ReflectionTestUtils.getField(jwtUtils, "refreshTokenExpiration");

        assertEquals(900000L, accessExp);
        assertEquals(2592000000L, refreshExp);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String role = "AUDIENCE";

        String token = jwtUtils.generateAccessToken(userId, role);
        assertNotNull(token);
        assertTrue(jwtUtils.validateAccessToken(token));

        assertEquals(userId, jwtUtils.extractIdFromAccessToken(token));
        assertEquals(role, jwtUtils.extractRoleFromAccessToken(token));
        assertNotNull(jwtUtils.extractJtiFromAccessToken(token));
    }
}
