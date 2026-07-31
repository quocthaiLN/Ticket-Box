package com.ticketbox.api.module.audit.repositories;

import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

}





