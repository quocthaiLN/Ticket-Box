package com.ticketbox.api.module.audit.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.audit.domain.exception.AuditLogNotFoundException;
import com.ticketbox.api.module.audit.repositories.AuditLogRepository;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.audit.domain.mappers.AuditLogMapper;
import com.ticketbox.api.module.audit.domain.mappers.AuditLogMapperImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private AuditLogMapper auditLogMapper = new AuditLogMapperImpl();

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private User sampleUser;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        auditLogMapper.setObjectMapper(objectMapper);
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@ticketbox.vn")
                .fullName("System Admin")
                .role(UserRole.ADMIN)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(sampleUser);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("127.0.0.1");
        mockRequest.addHeader("User-Agent", "Test-Browser/1.0");
        mockRequest.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void logBusinessSuccess_savesAuditLogWithResolvedActorAndRequestDetails() {
        Map<String, Object> metadata = Map.of(
                "title", "Concert Title",
                "secret_key", "SuperSecret123",
                "password", "PlainPassword!"
        );

        auditLogService.logBusinessSuccess(AuditAction.CREATE_CONCERT, "CONCERT", "crt-123", metadata);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(sampleUser, saved.getActor());
        assertEquals(AuditAction.CREATE_CONCERT, saved.getAction());
        assertEquals("CONCERT", saved.getEntityType());
        assertEquals("crt-123", saved.getEntityId());
        assertEquals("203.0.113.195", saved.getIpAddress());
        assertEquals("Test-Browser/1.0", saved.getUserAgent());

        // Verify metadata sanitization: password and secret_key should be REDACTED
        assertNotNull(saved.getMetadata());
        assertTrue(saved.getMetadata().contains("***REDACTED***"));
        assertFalse(saved.getMetadata().contains("SuperSecret123"));
        assertFalse(saved.getMetadata().contains("PlainPassword!"));
        assertTrue(saved.getMetadata().contains("Concert Title"));
    }

    @Test
    void logBusinessSuccess_doesNotSwallowException_whenDatabaseSaveFails() {
        doThrow(new DataIntegrityViolationException("DB error"))
                .when(auditLogRepository).save(any(AuditLog.class));

        assertThrows(DataIntegrityViolationException.class, () ->
                auditLogService.logBusinessSuccess(AuditAction.CREATE_CONCERT, "CONCERT", "crt-123", Map.of()));
    }

    @Test
    void logInvestigativeFailure_swallowsExceptionAndLogsSystemError_whenDatabaseFails() {
        doThrow(new RuntimeException("DB Connection Timeout"))
                .when(auditLogRepository).save(any(AuditLog.class));

        // Must NOT throw exception to caller
        assertDoesNotThrow(() ->
                auditLogService.logInvestigativeFailure(AuditAction.LOGIN_FAILED, "USER", "user-123", Map.of("reason", "FAIL")));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void logInvestigativeFailure_savesSuccessfully() {
        auditLogService.logInvestigativeFailure(AuditAction.LOGIN_FAILED, "USER", "user-123", Map.of("reason", "BAD_CREDENTIALS"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(AuditAction.LOGIN_FAILED, saved.getAction());
        assertEquals("USER", saved.getEntityType());
        assertEquals("user-123", saved.getEntityId());
    }

    @Test
    void getAuditLogs_returnsPagedResponse() {
        UUID id = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(id)
                .actor(sampleUser)
                .action(AuditAction.UPDATE_CONCERT)
                .entityType("CONCERT")
                .entityId("c-1")
                .ipAddress("127.0.0.1")
                .userAgent("Agent")
                .createdAt(LocalDateTime.now())
                .build();

        Page<AuditLog> page = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLogResponse> result = auditLogService.getAuditLogs(
                AuditAction.UPDATE_CONCERT, sampleUser.getId(), "CONCERT", "c-1", null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(AuditAction.UPDATE_CONCERT, result.getContent().get(0).getAction());
        assertEquals(sampleUser.getEmail(), result.getContent().get(0).getActor().getEmail());
    }

    @Test
    void getAuditLogById_returnsDetailResponse_whenFound() {
        UUID id = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(id)
                .actor(sampleUser)
                .action(AuditAction.UPDATE_CONCERT)
                .entityType("CONCERT")
                .entityId("c-1")
                .metadata("{\"key\":\"value\"}")
                .ipAddress("127.0.0.1")
                .userAgent("Agent")
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogRepository.findById(id)).thenReturn(Optional.of(auditLog));

        AuditLogDetailResponse response = auditLogService.getAuditLogById(id);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals(AuditAction.UPDATE_CONCERT, response.getAction());
        assertNotNull(response.getMetadata());
    }

    @Test
    void getAuditLogById_throwsAuditLogNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(auditLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AuditLogNotFoundException.class, () -> auditLogService.getAuditLogById(id));
    }
}
