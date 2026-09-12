package com.ticketbox.api.infrastructure.security;

import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OidcSuccessHandlerTest {

    @Test
    void rejectsGoogleAccountWithUnverifiedEmail() throws Exception {
        UserService userService = mock(UserService.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getClaim("email_verified")).thenReturn(false);
        OidcSuccessHandler handler = new OidcSuccessHandler(userService, "http://localhost:3001/oauth2/callback");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(oidcUser, null));

        assertEquals(401, response.getStatus());
        verifyNoInteractions(userService);
    }

    @Test
    void returnsTokensAndRefreshCookieForVerifiedGoogleAccount() throws Exception {
        UserService userService = mock(UserService.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getClaim("email_verified")).thenReturn(true);
        when(oidcUser.getEmail()).thenReturn("google@example.com");
        when(oidcUser.getFullName()).thenReturn("Google User");
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(userService.loginWithGoogle("google-subject", "google@example.com", "Google User"))
                .thenReturn(LoginResponse.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .expiresIn(900)
                        .build());
        OidcSuccessHandler handler = new OidcSuccessHandler(userService, "http://localhost:3001/oauth2/callback");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(oidcUser, null));

        assertEquals(302, response.getStatus());
        assertTrue(response.getHeader("Set-Cookie").contains("refresh_token=refresh-token"));
        assertEquals("http://localhost:3001/oauth2/callback", response.getRedirectedUrl());
    }
}
