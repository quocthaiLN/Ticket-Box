package com.ticketbox.api.module.audit.domain.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.audit.domain.dtos.ActorDto;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogMapperTest {

    private AuditLogMapper auditLogMapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogMapper = new AuditLogMapperImpl();
        auditLogMapper.setObjectMapper(objectMapper);
    }

    @Test
    @DisplayName("AuditLogMapper: toResponse correctly maps all fields from AuditLog")
    void testToResponse() {
        UUID logId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User actor = User.builder()
                .id(userId)
                .email("user@ticketbox.vn")
                .fullName("Test User")
                .role(UserRole.ADMIN)
                .build();

        LocalDateTime now = LocalDateTime.now();
        AuditLog auditLog = AuditLog.builder()
                .id(logId)
                .actor(actor)
                .action(AuditAction.CREATE_CONCERT)
                .entityType("CONCERT")
                .entityId("c-123")
                .metadata("{\"title\":\"Show\"}")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .createdAt(now)
                .build();

        AuditLogResponse response = auditLogMapper.toResponse(auditLog);

        assertNotNull(response);
        assertEquals(logId, response.getId());
        assertEquals(AuditAction.CREATE_CONCERT, response.getAction());
        assertEquals("CONCERT", response.getEntityType());
        assertEquals("c-123", response.getEntityId());
        assertEquals("192.168.1.1", response.getIpAddress());
        assertEquals("Mozilla/5.0", response.getUserAgent());
        assertEquals(now, response.getCreatedAt());

        assertNotNull(response.getActor());
        assertEquals(userId, response.getActor().getId());
        assertEquals("user@ticketbox.vn", response.getActor().getEmail());
        assertEquals("Test User", response.getActor().getFullName());
    }

    @Test
    @DisplayName("AuditLogMapper: toResponse handles null actor gracefully")
    void testToResponse_nullActor() {
        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(null)
                .action(AuditAction.LOGIN_FAILED)
                .entityType("USER")
                .entityId("u-456")
                .createdAt(LocalDateTime.now())
                .build();

        AuditLogResponse response = auditLogMapper.toResponse(auditLog);

        assertNotNull(response);
        assertNull(response.getActor());
        assertEquals(AuditAction.LOGIN_FAILED, response.getAction());
    }

    @Test
    @DisplayName("AuditLogMapper: toDetailResponse deserializes JSON metadata properly")
    void testToDetailResponse_withJsonMetadata() {
        UUID logId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(logId)
                .action(AuditAction.UPDATE_USER_STATUS)
                .entityType("USER")
                .entityId("u-789")
                .metadata("{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"SUSPENDED\"}")
                .createdAt(LocalDateTime.now())
                .build();

        AuditLogDetailResponse response = auditLogMapper.toDetailResponse(auditLog);

        assertNotNull(response);
        assertEquals(logId, response.getId());
        assertEquals(AuditAction.UPDATE_USER_STATUS, response.getAction());
        assertNotNull(response.getMetadata());
        assertTrue(response.getMetadata().toString().contains("SUSPENDED"));
    }

    @Test
    @DisplayName("AuditLogMapper: toDetailResponse handles null and invalid JSON metadata")
    void testToDetailResponse_nullAndInvalidMetadata() {
        AuditLog logWithNullMeta = AuditLog.builder()
                .id(UUID.randomUUID())
                .metadata(null)
                .build();
        assertNull(auditLogMapper.toDetailResponse(logWithNullMeta).getMetadata());

        AuditLog logWithEmptyMeta = AuditLog.builder()
                .id(UUID.randomUUID())
                .metadata("   ")
                .build();
        assertNull(auditLogMapper.toDetailResponse(logWithEmptyMeta).getMetadata());

        AuditLog logWithMalformedMeta = AuditLog.builder()
                .id(UUID.randomUUID())
                .metadata("{invalid-json")
                .build();
        assertEquals("{invalid-json", auditLogMapper.toDetailResponse(logWithMalformedMeta).getMetadata());
    }

    @Test
    @DisplayName("AuditLogMapper: toActorDto maps User correctly")
    void testToActorDto() {
        assertNull(auditLogMapper.toActorDto(null));

        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@ticketbox.vn")
                .fullName("Test Name")
                .build();

        ActorDto dto = auditLogMapper.toActorDto(user);
        assertNotNull(dto);
        assertEquals(userId, dto.getId());
        assertEquals("test@ticketbox.vn", dto.getEmail());
        assertEquals("Test Name", dto.getFullName());
    }
}
