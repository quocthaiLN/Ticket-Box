package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.infrastructure.security.TokenBlacklistService;
import com.ticketbox.api.module.auth.domain.dtos.LoginRequest;
import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.domain.dtos.RegisterRequest;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.repositories.UserAccountRepository;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private UserAccount sampleAccount;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .fullName("Test User")
                .role(User.UserRole.AUDIENCE)
                .status(User.UserStatus.ACTIVE)
                .build();

        sampleAccount = UserAccount.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .provider("LOCAL")
                .providerUserId("test@example.com")
                .passwordHash("hashed_password")
                .build();

        sampleUser.setAccounts(Set.of(sampleAccount));
    }

    @Test
    void register_success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("Password123!")
                .confirmPassword("Password123!")
                .fullName("New User")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(bCryptPasswordEncoder.encode(request.getPassword())).thenReturn("hashed_pass");

        UserResponse response = userService.register(request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void register_passwordMismatch_throwsException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("Password123!")
                .confirmPassword("DifferentPass!")
                .fullName("New User")
                .build();

        AppException ex = assertThrows(AppException.class, () -> userService.register(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("PASSWORD_MISMATCH", ex.getErrorCode());
    }

    @Test
    void login_success() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        when(bCryptPasswordEncoder.matches(request.getPassword(), "hashed_password")).thenReturn(true);
        when(jwtUtils.generateAccessToken(sampleUser.getId(), "AUDIENCE")).thenReturn("mocked_access_token");
        when(jwtUtils.generateRefreshToken(sampleUser.getId(), "AUDIENCE")).thenReturn("mocked_refresh_token");
        when(jwtUtils.getAccessTokenExpirationMillis()).thenReturn(900000L);

        LoginResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals("mocked_access_token", response.getAccessToken());
        assertEquals("mocked_refresh_token", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    void login_invalidPassword_throwsUnauthorized() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("WrongPassword")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        when(bCryptPasswordEncoder.matches(request.getPassword(), "hashed_password")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> userService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    void logout_blacklistsRefreshToken() {
        String refreshToken = "valid_refresh_token";
        when(jwtUtils.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtils.extractJtiFromRefreshToken(refreshToken)).thenReturn("mock_jti");
        when(jwtUtils.getRemainingTtlMillisFromRefreshToken(refreshToken)).thenReturn(3600000L);

        userService.logout(refreshToken);

        verify(tokenBlacklistService).blacklistToken("mock_jti", 3600000L);
    }
}
