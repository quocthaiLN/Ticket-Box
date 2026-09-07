package com.ticketbox.api.module.auth.services;


import com.ticketbox.api.module.auth.domain.dtos.*;



import java.util.UUID;

public interface UserService {

    UserResponse register(RegisterRequest request);

    UserResponse verifyOtp(VerifyOtpRequest request);

    void resendOtp(ResendOtpRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    LoginResponse refreshToken(String refreshToken);

    UserResponse getProfile(UUID userId);
}
