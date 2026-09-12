package com.ticketbox.api.infrastructure.security;

import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.services.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OidcSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final String successRedirectUri;

    public OidcSuccessHandler(UserService userService,
                              @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
        this.userService = userService;
        this.successRedirectUri = successRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)
                || !Boolean.TRUE.equals(oidcUser.getClaim("email_verified"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google email is not verified");
            return;
        }

        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();
        if (email == null || email.isBlank() || fullName == null || fullName.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google account is missing required profile information");
            return;
        }

        LoginResponse loginResponse = userService.loginWithGoogle(oidcUser.getSubject(), email, fullName);
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", loginResponse.getRefreshToken())
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        response.sendRedirect(successRedirectUri);
    }
}
