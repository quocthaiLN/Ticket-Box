package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.infrastructure.security.TokenBlacklistService;
import com.ticketbox.api.module.auth.domain.dtos.LoginRequest;
import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.domain.dtos.RegisterRequest;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.domain.entities.UserAccountStatus;
import com.ticketbox.api.module.auth.domain.entities.UserProvider;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.exception.AccountDisabledException;
import com.ticketbox.api.module.auth.domain.exception.AccountNotVerifiedException;
import com.ticketbox.api.module.auth.domain.exception.AlreadyVerifiedException;
import com.ticketbox.api.module.auth.domain.exception.AuthErrorCode;
import com.ticketbox.api.module.auth.domain.exception.EmailAlreadyExistsException;
import com.ticketbox.api.module.auth.domain.exception.ExpiredRefreshTokenException;
import com.ticketbox.api.module.auth.domain.exception.InvalidCredentialsException;
import com.ticketbox.api.module.auth.domain.exception.InvalidOtpException;
import com.ticketbox.api.module.auth.domain.exception.PhoneAlreadyExistsException;
import com.ticketbox.api.module.auth.domain.exception.RevokedRefreshTokenException;
import com.ticketbox.api.module.auth.domain.exception.UserNotFoundException;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import com.ticketbox.api.module.auth.repositories.UserAccountRepository;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Mock
    private com.ticketbox.api.module.auth.producer.AuthProducer authProducer;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private UserAccount sampleAccount;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.AUDIENCE)
                .status(UserStatus.ACTIVE)
                .build();

        sampleAccount = UserAccount.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .provider(UserProvider.LOCAL)
                .userAccountStatus(UserAccountStatus.ACTIVE)
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

        RequestValidationException ex = assertThrows(RequestValidationException.class, () -> userService.register(request));
        assertEquals("PASSWORD_MISMATCH", ex.getCode());
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

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> userService.login(request));
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode().code());
        assertEquals(com.ticketbox.api.module.shared.exception.ErrorType.UNAUTHORIZED, ex.getErrorCode().type());
    }

    @Test
    void loginWithGoogle_createsActiveUserAndGoogleAccount() {
        String googleSubject = "google-subject";
        when(userAccountRepository.findByProviderAndProviderUserId(UserProvider.GOOGLE, googleSubject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtUtils.generateAccessToken(sampleUser.getId(), "AUDIENCE")).thenReturn("google_access_token");
        when(jwtUtils.generateRefreshToken(sampleUser.getId(), "AUDIENCE")).thenReturn("google_refresh_token");
        when(jwtUtils.getAccessTokenExpirationMillis()).thenReturn(900000L);

        LoginResponse response = userService.loginWithGoogle(googleSubject, "google@example.com", "Google User");

        assertEquals("google_access_token", response.getAccessToken());
        verify(userRepository).save(any(User.class));
        verify(userAccountRepository).save(argThat(account ->
                account.getProvider() == UserProvider.GOOGLE
                        && googleSubject.equals(account.getProviderUserId())
                        && account.getPasswordHash() == null));
    }

    @Test
    void loginWithGoogle_rejectsDisabledAccount() {
        sampleUser.setStatus(UserStatus.SUSPENDED);
        sampleAccount.setProvider(UserProvider.GOOGLE);
        sampleAccount.setProviderUserId("google-subject");
        when(userAccountRepository.findByProviderAndProviderUserId(UserProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.of(sampleAccount));

        AccountDisabledException ex = assertThrows(AccountDisabledException.class,
                () -> userService.loginWithGoogle("google-subject", "test@example.com", "Test User"));

        assertEquals("ACCOUNT_DISABLED", ex.getErrorCode().code());
    }

    @Test
    void register_rejectsExistingEmailAndPhone() {
        RegisterRequest request = RegisterRequest.builder().email("test@example.com").password("secret").build();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        assertEquals("EMAIL_ALREADY_EXISTS", assertThrows(EmailAlreadyExistsException.class,
                () -> userService.register(request)).getErrorCode().code());

        request.setEmail("new@example.com");
        request.setPhone("123");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone("123")).thenReturn(true);
        assertEquals("PHONE_ALREADY_EXISTS", assertThrows(PhoneAlreadyExistsException.class,
                () -> userService.register(request)).getErrorCode().code());
    }

    @Test
    void verifyOtp_rejectsInvalidOtpMissingUserAndAlreadyVerifiedAccount() {
        var request = com.ticketbox.api.module.auth.domain.dtos.VerifyOtpRequest.builder()
                .email("test@example.com").otp("000000").build();
        when(valueOperations.get("otp:register:test@example.com")).thenReturn("111111");
        assertEquals("INVALID_OTP", assertThrows(InvalidOtpException.class,
                () -> userService.verifyOtp(request)).getErrorCode().code());

        when(valueOperations.get("otp:register:test@example.com")).thenReturn("000000");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        assertEquals("USER_NOT_FOUND", assertThrows(UserNotFoundException.class,
                () -> userService.verifyOtp(request)).getErrorCode().code());

        sampleUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        assertEquals("ALREADY_VERIFIED", assertThrows(AlreadyVerifiedException.class,
                () -> userService.verifyOtp(request)).getErrorCode().code());
    }

    @Test
    void login_rejectsUnverifiedAccount() {
        sampleUser.setStatus(UserStatus.PENDING);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        var request = LoginRequest.builder().email("test@example.com").password("secret").build();
        assertEquals("ACCOUNT_NOT_VERIFIED", assertThrows(AccountNotVerifiedException.class,
                () -> userService.login(request)).getErrorCode().code());
    }

    @Test
    void refreshToken_rejectsExpiredAndRevokedTokens() {
        when(jwtUtils.validateRefreshToken("expired")).thenReturn(false);
        assertEquals("TOKEN_EXPIRED", assertThrows(ExpiredRefreshTokenException.class,
                () -> userService.refreshToken("expired")).getErrorCode().code());

        when(jwtUtils.validateRefreshToken("revoked")).thenReturn(true);
        when(jwtUtils.extractJtiFromRefreshToken("revoked")).thenReturn("jti");
        when(tokenBlacklistService.isBlacklisted("jti")).thenReturn(true);
        assertEquals("TOKEN_REVOKED", assertThrows(RevokedRefreshTokenException.class,
                () -> userService.refreshToken("revoked")).getErrorCode().code());
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
