package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.infrastructure.security.TokenBlacklistService;
import com.ticketbox.api.module.auth.domain.dtos.*;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.entities.UserProvider;
import com.ticketbox.api.module.auth.domain.entities.UserAccountStatus;
import com.ticketbox.api.module.auth.producer.AuthProducer;
import com.ticketbox.api.module.auth.repositories.UserAccountRepository;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
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
            throw new AppException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH",
                    "Password and confirm password do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS", "Phone number is already registered");
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
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Invalid or expired OTP code");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ALREADY_VERIFIED", "Account is already verified or not in pending state");
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
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ALREADY_VERIFIED", "Account is already verified");
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
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));

        if (user.getStatus() == UserStatus.PENDING) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_VERIFIED",
                    "Account has not been verified via OTP");
        }

        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Account is disabled or locked");
        }

        UserAccount localAccount = user.getAccounts().stream()
                .filter(acc -> acc.getProvider().equals(UserProvider.LOCAL)
                        && acc.getUserAccountStatus() == UserAccountStatus.ACTIVE
                        && acc.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));

        if (!bCryptPasswordEncoder.matches(request.getPassword(), localAccount.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }

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
            throw new AppException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Refresh token is invalid or expired");
        }

        String jti = jwtUtils.extractJtiFromRefreshToken(refreshToken);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "Refresh token has been revoked");
        }

        UUID userId = jwtUtils.extractIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Account is disabled or locked");
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
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        return UserResponse.fromEntity(user);
    }
}
