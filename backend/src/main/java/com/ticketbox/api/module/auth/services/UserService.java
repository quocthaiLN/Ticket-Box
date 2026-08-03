package com.ticketbox.api.module.auth.services;


import com.ticketbox.api.module.auth.domain.dtos.LoginRequest;
import com.ticketbox.api.module.auth.domain.dtos.LoginResponse;
import com.ticketbox.api.module.auth.domain.dtos.RegisterRequest;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;


import java.util.UUID;

public interface UserService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    LoginResponse refreshToken(String refreshToken);

    UserResponse getProfile(UUID userId);
}
