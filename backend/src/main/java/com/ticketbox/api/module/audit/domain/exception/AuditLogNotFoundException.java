package com.ticketbox.api.module.audit.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;

import java.util.Map;
import java.util.UUID;

public final class AuditLogNotFoundException extends NotFoundException {
    public AuditLogNotFoundException(UUID id) {
        super(AuditErrorCode.AUDIT_LOG_NOT_FOUND, "Audit log record not found with id: " + id,
                Map.of("id", id != null ? id.toString() : "null"));
    }
}
