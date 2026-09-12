package com.ticketbox.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OidcFailureHandlerTest {

    @Test
    void redirectsToFrontendWithoutExposingAuthenticationFailureDetails() throws Exception {
        OidcFailureHandler handler = new OidcFailureHandler("http://localhost:3001/login?error=oauth2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("provider error"));

        assertEquals(302, response.getStatus());
        assertEquals("http://localhost:3001/login?error=oauth2", response.getRedirectedUrl());
    }
}
