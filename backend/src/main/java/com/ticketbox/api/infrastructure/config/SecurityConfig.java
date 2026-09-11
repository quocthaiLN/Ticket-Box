package com.ticketbox.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.response.ErrorResponse;
import com.ticketbox.api.infrastructure.security.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = new ObjectMapper();
    }

    @Bean
    public static BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler()))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/verify-otp", "/auth/resend-otp",
                                "/auth/login", "/auth/refresh", "/concerts/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                List.of("http://localhost:3000", "http://localhost:3001", "http://10.0.148.2:3001/"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String jwtError = (String) request.getAttribute("JWT_ERROR");
            String errorCode = "UNAUTHORIZED";
            String errorMessage = "Authentication is required to access this resource";

            if ("TOKEN_REVOKED".equals(jwtError)) {
                errorCode = "TOKEN_REVOKED";
                errorMessage = "JWT token has been revoked";
            }

            ErrorResponse errorResponse = ErrorResponse.of(errorCode, errorMessage);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = ErrorResponse.of("FORBIDDEN", "Access denied: insufficient permissions");
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }
}