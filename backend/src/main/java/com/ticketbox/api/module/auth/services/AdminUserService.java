package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {
    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse updateUserStatus(UUID userId, User.UserStatus newStatus, UUID adminId, String ipAddress, String userAgent);
}
