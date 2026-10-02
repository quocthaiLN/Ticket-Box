package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.infrastructure.security.TokenBlacklistService;
import com.ticketbox.api.module.auth.domain.dtos.*;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.entities.UserProvider;
import com.ticketbox.api.module.auth.domain.entities.UserAccountStatus;
import com.ticketbox.api.module.auth.domain.exception.AccountDisabledException;
import com.ticketbox.api.module.auth.domain.exception.AccountNotVerifiedException;
import com.ticketbox.api.module.auth.domain.exception.AlreadyVerifiedException;
import com.ticketbox.api.module.auth.domain.exception.EmailAlreadyExistsException;
import com.ticketbox.api.module.auth.domain.exception.ExpiredRefreshTokenException;
import com.ticketbox.api.module.auth.domain.exception.InvalidCredentialsException;
import com.ticketbox.api.module.auth.domain.exception.InvalidOtpException;
import com.ticketbox.api.module.auth.domain.exception.PhoneAlreadyExistsException;
import com.ticketbox.api.module.auth.domain.exception.RevokedRefreshTokenException;
import com.ticketbox.api.module.auth.domain.exception.UserNotFoundException;
import com.ticketbox.api.module.auth.producer.AuthProducer;
import com.ticketbox.api.module.auth.repositories.UserAccountRepository;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;
import com.ticketbox.api.module.shared.validation.RequestValidationException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthProducer authProducer;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.otp.ttl-minutes:5}")
    private long otpTtlMinutes;

    private static final String OTP_KEY_PREFIX = "otp:register:";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new RequestValidationException("PASSWORD_MISMATCH",
                    "Password and confirm password do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone())) {
            throw new PhoneAlreadyExistsException();
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(UserRole.AUDIENCE)
                .status(UserStatus.PENDING)
                .build();

        User savedUser = userRepository.save(user);

        UserAccount userAccount = UserAccount.builder()
                .user(savedUser)
                .provider(UserProvider.LOCAL)
                .providerUserId(savedUser.getEmail())
                .passwordHash(bCryptPasswordEncoder.encode(request.getPassword()))
                .build();

        userAccountRepository.save(userAccount);

        // Generate 6-digit random OTP code
        String otpCode = String.format("%06d", secureRandom.nextInt(1000000));

        // Save OTP code to Redis with configured TTL
        String redisKey = OTP_KEY_PREFIX + savedUser.getEmail();
        stringRedisTemplate.opsForValue().set(redisKey, otpCode, otpTtlMinutes, TimeUnit.MINUTES);

        // Publish message to RabbitMQ for NotificationConsumer to send email
        AuthOtpMessageDTO otpMessage = AuthOtpMessageDTO.builder()
                .userId(savedUser.getId().toString())
                .email(savedUser.getEmail())
                .otp(otpCode)
                .otpType("REGISTER_VERIFICATION")
                .expirationTime(System.currentTimeMillis() + (otpTtlMinutes * 60 * 1000))
                .build();

        authProducer.sendOtpMessage(otpMessage);

        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public UserResponse verifyOtp(VerifyOtpRequest request) {
        String redisKey = OTP_KEY_PREFIX + request.getEmail();
        String cachedOtp = stringRedisTemplate.opsForValue().get(redisKey);

        if (cachedOtp == null || !cachedOtp.equals(request.getOtp())) {
            throw new InvalidOtpException();
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new AlreadyVerifiedException("Account is already verified or not in pending state");
        }

        user.setStatus(UserStatus.ACTIVE);
        User updatedUser = userRepository.save(user);

        // Clear OTP from Redis upon successful verification
        stringRedisTemplate.delete(redisKey);

        return UserResponse.fromEntity(updatedUser);
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new AlreadyVerifiedException("Account is already verified");
        }

        String otpCode = String.format("%06d", secureRandom.nextInt(1000000));
        String redisKey = OTP_KEY_PREFIX + user.getEmail();
        stringRedisTemplate.opsForValue().set(redisKey, otpCode, otpTtlMinutes, TimeUnit.MINUTES);

        AuthOtpMessageDTO otpMessage = AuthOtpMessageDTO.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .otp(otpCode)
                .otpType("REGISTER_VERIFICATION")
                .expirationTime(System.currentTimeMillis() + (otpTtlMinutes * 60 * 1000))
                .build();

        authProducer.sendOtpMessage(otpMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() == UserStatus.PENDING) {
            throw new AccountNotVerifiedException();
        }

        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new AccountDisabledException();
        }

        UserAccount localAccount = user.getAccounts().stream()
                .filter(acc -> acc.getProvider().equals(UserProvider.LOCAL)
                        && acc.getUserAccountStatus() == UserAccountStatus.ACTIVE
                        && acc.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(InvalidCredentialsException::new);

        if (!bCryptPasswordEncoder.matches(request.getPassword(), localAccount.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return createLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(String providerUserId, String email, String fullName) {
        User user = userAccountRepository.findByProviderAndProviderUserId(UserProvider.GOOGLE, providerUserId)
                .map(UserAccount::getUser)
                .orElseGet(() -> findOrCreateGoogleUser(providerUserId, email, fullName));

        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new AccountDisabledException();
        }

        return createLoginResponse(user);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && jwtUtils.validateRefreshToken(refreshToken)) {
            String jti = jwtUtils.extractJtiFromRefreshToken(refreshToken);
            long remainingTtl = jwtUtils.getRemainingTtlMillisFromRefreshToken(refreshToken);
            if (jti != null && remainingTtl > 0) {
                tokenBlacklistService.blacklistToken(jti, remainingTtl);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || !jwtUtils.validateRefreshToken(refreshToken)) {
            throw new ExpiredRefreshTokenException();
        }

        String jti = jwtUtils.extractJtiFromRefreshToken(refreshToken);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            throw new RevokedRefreshTokenException();
        }

        UUID userId = jwtUtils.extractIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        String newAccessToken = jwtUtils.generateAccessToken(user.getId(), user.getRole().name());
        long expiresIn = jwtUtils.getAccessTokenExpirationMillis() / 1000;

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(expiresIn)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.fromEntity(user);
    }

    private User findOrCreateGoogleUser(String providerUserId, String email, String fullName) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .fullName(fullName)
                        .role(UserRole.AUDIENCE)
                        .status(UserStatus.ACTIVE)
                        .build()));

        UserAccount googleAccount = UserAccount.builder()
                .user(user)
                .provider(UserProvider.GOOGLE)
                .providerUserId(providerUserId)
                .build();
        userAccountRepository.save(googleAccount);
        return user;
    }

    private LoginResponse createLoginResponse(User user) {
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getRole().name());
        long expiresIn = jwtUtils.getAccessTokenExpirationMillis() / 1000;

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
