package com.ticketbox.api.module.audit.services;

import com.ticketbox.api.module.auth.domain.entities.User;

import java.util.Map;

public interface AuditLogService {
    void logAction(User actor, String action, String entityType, String entityId, Map<String, Object> metadata, String ipAddress, String userAgent);
}
