package com.ticketbox.api.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OidcFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OidcFailureHandler.class);

    private final String failureRedirectUri;

    public OidcFailureHandler(@Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri) {
        this.failureRedirectUri = failureRedirectUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("OIDC authentication failed", exception);
        response.sendRedirect(failureRedirectUri);
    }
}
