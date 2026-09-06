package com.ticketbox.api.module.auth.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.auth.domain.dtos.LoginRequest;
import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.services.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void login_setsRefreshTokenCookie_andReturnsAccessToken() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("user@example.com")
                .password("password123")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("User Example")
                .role(UserRole.AUDIENCE)
                .status(UserStatus.ACTIVE)
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock_access_token")
                .refreshToken("mock_refresh_token")
                .expiresIn(3600)
                .user(userResponse)
                .build();

        when(userService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=mock_refresh_token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/auth/refresh")))
                .andExpect(jsonPath("$.data.access_token").value("mock_access_token"));
    }

    @Test
    void logout_clearsRefreshTokenCookie() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "mock_refresh_token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        org.mockito.Mockito.verify(userService).logout("mock_refresh_token");
    }

    @Test
    void refresh_readsRefreshTokenFromCookie() throws Exception {
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("new_access_token")
                .expiresIn(3600)
                .build();

        when(userService.refreshToken("valid_refresh_token")).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "valid_refresh_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("new_access_token"));
    }
}
