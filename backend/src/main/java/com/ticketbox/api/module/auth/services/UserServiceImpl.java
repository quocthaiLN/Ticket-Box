package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.infrastructure.security.TokenBlacklistService;
import com.ticketbox.api.module.auth.domain.dtos.*;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.repositories.UserAccountRepository;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Password and confirm password do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS", "Phone number is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.UserRole.AUDIENCE)
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        UserAccount userAccount = UserAccount.builder()
                .user(savedUser)
                .provider("LOCAL")
                .providerUserId(savedUser.getEmail())
                .passwordHash(bCryptPasswordEncoder.encode(request.getPassword()))
                .build();

        userAccountRepository.save(userAccount);

        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"));

        if (user.getStatus() == User.UserStatus.LOCKED || user.getStatus() == User.UserStatus.DISABLED || user.getStatus() == User.UserStatus.BLOCKED) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Account is disabled or locked");
        }

        UserAccount localAccount = user.getAccounts().stream()
                .filter(acc -> "LOCAL".equalsIgnoreCase(acc.getProvider()))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"));

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

        if (user.getStatus() != User.UserStatus.ACTIVE) {
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
