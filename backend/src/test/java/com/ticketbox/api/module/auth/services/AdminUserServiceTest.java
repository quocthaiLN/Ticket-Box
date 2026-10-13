package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.exception.UserNotFoundException;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @org.mockito.Spy private com.ticketbox.api.module.auth.domain.mappers.UserMapper userMapper = new com.ticketbox.api.module.auth.domain.mappers.UserMapperImpl();
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
        com.ticketbox.api.module.auth.domain.entities.User user = com.ticketbox.api.module.auth.domain.entities.User.builder()
                .id(userId)
                .email("test@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(com.ticketbox.api.module.auth.domain.entities.User.class))).thenReturn(user);

        adminUserService.updateUserStatus(userId, UserStatus.ACTIVE, null, null, null);

        org.mockito.Mockito.verify(auditLogService).logBusinessSuccess(
                org.mockito.ArgumentMatchers.eq(com.ticketbox.api.module.audit.domain.entities.AuditAction.UPDATE_USER_STATUS),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                org.mockito.ArgumentMatchers.argThat(map -> "SUSPENDED".equals(map.get("old_status")) && "ACTIVE".equals(map.get("new_status")))
        );
    }
}
