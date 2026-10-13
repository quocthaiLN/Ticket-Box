package com.ticketbox.api.module.audit.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketbox.api.infrastructure.exception.GlobalExceptionHandler;
import com.ticketbox.api.module.audit.domain.dtos.ActorDto;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.exception.AuditLogNotFoundException;
import com.ticketbox.api.module.audit.services.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditLogController(auditLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAuditLogs_returns200AndAuditsReadAction() throws Exception {
        UUID logId = UUID.randomUUID();
        AuditLogResponse logResponse = AuditLogResponse.builder()
                .id(logId)
                .action(AuditAction.UPDATE_CONCERT)
                .entityType("CONCERT")
                .entityId("c-123")
                .actor(ActorDto.builder().id(UUID.randomUUID()).email("admin@ticketbox.vn").fullName("Admin").build())
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogService.getAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(logResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.data[0].action").value("UPDATE_CONCERT"))
                .andExpect(jsonPath("$.pagination.total_items").value(1))
                .andExpect(jsonPath("$.pagination.page").value(0));

        // Auditing the Auditors: Verify that query action was audited
        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.READ_AUDIT_LOGS),
                eq("AUDIT_LOG"),
                eq("LIST"),
                any()
        );
    }

    @Test
    void getAuditLogs_withFilters_passesParametersToService() throws Exception {
        when(auditLogService.getAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        mockMvc.perform(get("/admin/audit-logs")
                        .param("action", "LOGIN_FAILED")
                        .param("entityType", "USER")
                        .param("entityId", "test@example.com")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(auditLogService).getAuditLogs(
                eq(AuditAction.LOGIN_FAILED),
                isNull(),
                eq("USER"),
                eq("test@example.com"),
                isNull(),
                isNull(),
                eq(PageRequest.of(1, 10))
        );
    }

    @Test
    void getAuditLogById_returnsDetailAndAuditsReadAction() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogDetailResponse detailResponse = AuditLogDetailResponse.builder()
                .id(id)
                .action(AuditAction.UPDATE_USER_STATUS)
                .entityType("USER")
                .entityId("u-456")
                .metadata(Map.of("old_status", "SUSPENDED", "new_status", "ACTIVE"))
                .actor(ActorDto.builder().id(UUID.randomUUID()).email("admin@ticketbox.vn").fullName("Admin").build())
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogService.getAuditLogById(id)).thenReturn(detailResponse);

        mockMvc.perform(get("/admin/audit-logs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.action").value("UPDATE_USER_STATUS"))
                .andExpect(jsonPath("$.data.metadata.old_status").value("SUSPENDED"));

        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.READ_AUDIT_LOGS),
                eq("AUDIT_LOG"),
                eq(id.toString()),
                any()
        );
    }

    @Test
    void getAuditLogById_notFound_returns404ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(auditLogService.getAuditLogById(id)).thenThrow(new AuditLogNotFoundException(id));

        mockMvc.perform(get("/admin/audit-logs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
