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

    @Test
    void testValidateTokenWithHexSecret() {
        JwtUtils customJwt = new JwtUtils();
        ReflectionTestUtils.setField(customJwt, "accessTokenSecret", "9fd9c265e56bf6ba2ac6f62f53a189eba9457b7bf3a0010a31649df22e0b942a");
        ReflectionTestUtils.setField(customJwt, "accessTokenExpirationRaw", "900000");
        ReflectionTestUtils.setField(customJwt, "refreshTokenSecret", "004edc7d0d12e37e71f2229712d9fcb3b3db5a52c87ddacbfd39677565fafefe");
        ReflectionTestUtils.setField(customJwt, "refreshTokenExpirationRaw", "2592000000");
        customJwt.init();

        UUID uid = UUID.randomUUID();
        String generated = customJwt.generateAccessToken(uid, "AUDIENCE");
        assertTrue(customJwt.validateAccessToken(generated));
        assertEquals(uid, customJwt.extractIdFromAccessToken(generated));
        assertEquals("AUDIENCE", customJwt.extractRoleFromAccessToken(generated));

        String nodeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI4Y2QwMzAxYi1jNmVhLTQ3OGYtODVjNS0yODBkZDRhZDEyMGEiLCJzdWIiOiJhMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJyb2xlIjoiQVVESUVOQ0UiLCJpYXQiOjE3OTIxMzE0MTYsImV4cCI6MTc5NDcyMzQxNn0.cX48dFbS2fiP1DtOoRKjs8xU2qyagx2k0m14FeIXkgU";
        assertTrue(customJwt.validateAccessToken(nodeToken));
        assertEquals(UUID.fromString("a0000000-0000-0000-0000-000000000001"), customJwt.extractIdFromAccessToken(nodeToken));
        assertEquals("AUDIENCE", customJwt.extractRoleFromAccessToken(nodeToken));
    }
}
