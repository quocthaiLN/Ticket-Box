package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.entities.UserAccountStatus;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromEntity);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(UUID userId, UserStatus newStatus, UUID adminId, String ipAddress,
            String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(newStatus);

        if (newStatus == UserStatus.DELETED) {
            LocalDateTime now = LocalDateTime.now();
            user.setDeletedAt(now);
            if (user.getAccounts() != null) {
                user.getAccounts().forEach(acc -> {
                    acc.setUserAccountStatus(UserAccountStatus.DELETED);
                    acc.setDeletedAt(now);
                });
            }
        } else if (newStatus == UserStatus.SUSPENDED) {
            if (user.getAccounts() != null) {
                user.getAccounts().forEach(acc -> {
                    if (acc.getUserAccountStatus() != UserAccountStatus.DELETED) {
                        acc.setUserAccountStatus(UserAccountStatus.SUSPENDED);
                    }
                });
            }
        } else if (newStatus == UserStatus.ACTIVE) {
            user.setDeletedAt(null);
            if (user.getAccounts() != null) {
                user.getAccounts().forEach(acc -> {
                    acc.setUserAccountStatus(UserAccountStatus.ACTIVE);
                    acc.setDeletedAt(null);
                });
            }
        }

        User updatedUser = userRepository.save(user);

        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        auditLogService.logAction(
                adminUser,
                "UPDATE_USER_STATUS",
                "User",
                user.getId().toString(),
                Map.of("old_status", oldStatus.name(), "new_status", newStatus.name()),
                ipAddress,
                userAgent);

        return UserResponse.fromEntity(updatedUser);
    }
}
