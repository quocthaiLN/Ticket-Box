package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.exception.UserNotFoundException;
import com.ticketbox.api.module.auth.domain.mappers.UserMapper;
import com.ticketbox.api.module.auth.domain.mappers.UserMapperImpl;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Spy private UserMapper userMapper = new UserMapperImpl();
    @InjectMocks private AdminUserServiceImpl adminUserService;

    @Test
    void updateStatus_missingUserThrowsAuthNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> adminUserService.updateUserStatus(userId, UserStatus.ACTIVE, null, null, null));

        assertEquals("USER_NOT_FOUND", exception.getErrorCode().code());
    }

    @Test
    void updateStatus_success_invokesAuditLogServiceBusinessSuccess() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        adminUserService.updateUserStatus(userId, UserStatus.ACTIVE, null, null, null);

        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.UPDATE_USER_STATUS),
                eq("USER"),
                eq(userId.toString()),
                argThat(map -> "SUSPENDED".equals(map.get("old_status")) && "ACTIVE".equals(map.get("new_status")))
        );
    }
}
