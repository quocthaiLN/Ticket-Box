package com.ticketbox.api.module.audit.repositories;

import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data Access Object cho bảng audit_logs.
 * Chỉ phục vụ các thao tác INSERT (save) và SELECT (find/findAll).
 * Không cung cấp các phương thức xóa hoặc cập nhật theo nguyên tắc bất biến.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

}
