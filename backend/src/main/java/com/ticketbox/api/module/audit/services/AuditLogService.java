package com.ticketbox.api.module.audit.services;

import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.auth.domain.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service ranh giới duy nhất để ghi và tra cứu Audit Trail trong hệ thống.
 * Tách biệt rõ 2 nhóm phương thức để quản lý transaction propagation tự động.
 */
public interface AuditLogService {

    /**
     * NHÓM 1: Ghi nhận thay đổi nghiệp vụ thành công.
     * Transaction Propagation: REQUIRED (chạy cùng transaction với thao tác nghiệp vụ).
     * Quy tắc lỗi: KHÔNG nuốt lỗi. Nếu ghi log thất bại, nghiệp vụ cha bắt buộc phải rollback.
     * Tự động trích xuất Actor từ SecurityContextHolder và IP/User-Agent từ RequestContextHolder.
     *
     * @param action     Hành động audit chuẩn hóa thuộc AuditAction enum
     * @param entityType Loại đối tượng bị tác động (e.g. CONCERT, USER, SEAT_ZONE)
     * @param entityId   Định danh đối tượng bị tác động (dạng chuỗi)
     * @param metadata   Dữ liệu bổ sung (trước/sau hoặc tham số nghiệp vụ)
     */
    void logBusinessSuccess(AuditAction action, String entityType, String entityId, Map<String, Object> metadata);

    /**
     * NHÓM 2: Ghi nhận sự kiện thất bại cần điều tra.
     * Transaction Propagation: REQUIRES_NEW (chạy trong transaction độc lập).
     * Phải được gọi TRƯỚC khi ném exception ra ngoài để tồn tại kể cả khi nghiệp vụ cha rollback.
     * Quy tắc lỗi: Có thể chấp nhận mất log trong trường hợp hiếm gặp (DB audit lỗi),
     * ghi log ra hệ thống (SLF4J logger.error) và không làm crash luồng ném exception ban đầu.
     *
     * @param action     Hành động audit chuẩn hóa (e.g. LOGIN_FAILED, ACCESS_DENIED)
     * @param entityType Loại đối tượng (e.g. USER, SECURITY)
     * @param entityId   Định danh đối tượng (e.g. email đã che, request URI)
     * @param metadata   Dữ liệu bổ sung (e.g. mã lỗi, lý do từ chối)
     */
    void logInvestigativeFailure(AuditAction action, String entityType, String entityId, Map<String, Object> metadata);

    /**
     * Phương thức legacy hỗ trợ tương thích tạm thời.
     */
    void logAction(User actor, String action, String entityType, String entityId, Map<String, Object> metadata, String ipAddress, String userAgent);

    /**
     * Tra cứu danh sách audit logs phân trang và lọc theo nhiều tiêu chí.
     */
    Page<AuditLogResponse> getAuditLogs(AuditAction action, UUID actorUserId, String entityType, String entityId,
                                        LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Xem thông tin chi tiết một bản ghi audit log kèm metadata.
     *
     * @throws com.ticketbox.api.module.audit.domain.exception.AuditLogNotFoundException nếu không tìm thấy ID
     */
    AuditLogDetailResponse getAuditLogById(UUID id);
}
