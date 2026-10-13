package com.ticketbox.api.module.audit.domain.dtos;

import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDetailResponse {
    private UUID id;
    private ActorDto actor;
    private AuditAction action;
    private String entityType;
    private String entityId;
    private Object metadata;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
