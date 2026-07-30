package com.ticketbox.api.module.audit;

import com.ticketbox.api.module.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

}





