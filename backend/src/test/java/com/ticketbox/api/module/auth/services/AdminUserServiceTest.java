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
    @InjectMocks private AdminUserServiceImpl adminUserService;

    @Test
    void updateStatus_missingUserThrowsAuthNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> adminUserService.updateUserStatus(userId, UserStatus.ACTIVE, null, null, null));

        assertEquals("USER_NOT_FOUND", exception.getErrorCode().code());
    }
}
