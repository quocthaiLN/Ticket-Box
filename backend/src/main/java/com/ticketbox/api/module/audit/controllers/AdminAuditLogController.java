package com.ticketbox.api.module.audit.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(Math.max(0, page), cappedSize);

        Page<AuditLogResponse> result = auditLogService.getAuditLogs(action, actorUserId, entityType, entityId, from, to, pageable);

        // Auditing the Auditors: Ghi nhận sự kiện Admin tra cứu danh sách audit logs
        auditLogService.logBusinessSuccess(AuditAction.READ_AUDIT_LOGS, "AUDIT_LOG", "LIST",
                Map.of("page", page, "size", cappedSize));

        ApiResponse.PaginationMeta paginationMeta = ApiResponse.PaginationMeta.builder()
                .page(result.getNumber())
                .pageSize(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasMore(result.hasNext())
                .build();

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), paginationMeta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditLogDetailResponse>> getAuditLogById(@PathVariable UUID id) {
        AuditLogDetailResponse response = auditLogService.getAuditLogById(id);

        // Auditing the Auditors: Ghi nhận sự kiện Admin xem chi tiết audit log
        auditLogService.logBusinessSuccess(AuditAction.READ_AUDIT_LOGS, "AUDIT_LOG", id.toString(), Map.of());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
