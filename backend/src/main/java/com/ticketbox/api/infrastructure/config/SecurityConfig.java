package com.ticketbox.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.response.ErrorResponse;
import com.ticketbox.api.infrastructure.response.AdminProblemWriter;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitFilter;
import com.ticketbox.api.infrastructure.rateLimit.RateLimitPolicyRegistry;
import com.ticketbox.api.infrastructure.rateLimit.RedisRateLimiter;
import com.ticketbox.api.infrastructure.security.JwtFilter;
import com.ticketbox.api.infrastructure.security.OidcFailureHandler;
import com.ticketbox.api.infrastructure.security.OidcSuccessHandler;

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
    private final OidcFailureHandler oidcFailureHandler;
    private final OidcSuccessHandler oidcSuccessHandler;
    private final RateLimitPolicyRegistry rateLimitPolicies;
    private final RedisRateLimiter redisRateLimiter;

    public SecurityConfig(JwtFilter jwtFilter, OidcSuccessHandler oidcSuccessHandler,
                          OidcFailureHandler oidcFailureHandler, RateLimitPolicyRegistry rateLimitPolicies,
                          RedisRateLimiter redisRateLimiter) {
        this.jwtFilter = jwtFilter;
        this.oidcSuccessHandler = oidcSuccessHandler;
        this.oidcFailureHandler = oidcFailureHandler;
        this.rateLimitPolicies = rateLimitPolicies;
        this.redisRateLimiter = redisRateLimiter;
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
                                "/auth/login", "/auth/refresh")
                        .permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/payments/vnpay/ipn")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/momo/ipn")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/concerts/*/quota")
                        .hasAnyRole("AUDIENCE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/concerts/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "ORGANIZER")
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oidcSuccessHandler)
                        .failureHandler(oidcFailureHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimitPolicies, redisRateLimiter, objectMapper), JwtFilter.class);

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
            if (AdminProblemWriter.write(objectMapper, request, response, 401, "UNAUTHORIZED",
                    "Authentication is required to access this resource")) return;
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
            if (AdminProblemWriter.write(objectMapper, request, response, 403, "FORBIDDEN",
                    "Access denied: insufficient permissions")) return;
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = ErrorResponse.of("FORBIDDEN", "Access denied: insufficient permissions");
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }
}
