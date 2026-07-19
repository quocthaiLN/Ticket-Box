package com.ticketbox.api.audit.repositories;

import com.ticketbox.api.audit.domain.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

}





