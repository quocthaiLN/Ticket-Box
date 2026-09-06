package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {
    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse updateUserStatus(UUID userId, UserStatus newStatus, UUID adminId, String ipAddress, String userAgent);
}
