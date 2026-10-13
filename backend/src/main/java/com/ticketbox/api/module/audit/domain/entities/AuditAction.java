package com.ticketbox.api.module.audit.domain.entities;

/**
 * Enum tập trung định danh tất cả các hành động được ghi trong hệ thống Audit Trail.
 * Không sử dụng chuỗi tự do (string literal) để đảm bảo tính nhất quán và toàn vẹn dữ liệu.
 */
public enum AuditAction {
    // =========================================================================
    // NHÓM 1: THAY ĐỔI NGHIỆP VỤ THÀNH CÔNG (Propagation.REQUIRED)
    // Ghi trong cùng transaction với thay đổi nghiệp vụ. Commit / Rollback cùng nhau.
    // Tuyệt đối không nuốt lỗi khi ghi log thất bại.
    // =========================================================================
    CREATE_CONCERT,
    UPDATE_CONCERT,
    PUBLISH_CONCERT,
    CANCEL_CONCERT,
    CREATE_SEAT_ZONE,
    UPDATE_SEAT_ZONE,
    CREATE_TICKET_TYPE,
    UPDATE_TICKET_TYPE,
    UPDATE_USER_STATUS,
    UPDATE_USER_ROLE,
    CREATE_USER,
    CREATE_ARTIST_BIO_JOB,
    READ_AUDIT_LOGS,

    // =========================================================================
    // NHÓM 2: SỰ KIỆN THẤT BẠI CẦN ĐIỀU TRA (Propagation.REQUIRES_NEW)
    // Ghi trong transaction riêng biệt để tồn tại độc lập kể cả khi nghiệp vụ cha rollback.
    // Phải được gọi trước khi ném exception ra ngoài.
    // Nuốt lỗi an toàn kèm ghi log hệ thống nếu DB audit gặp sự cố.
    // =========================================================================
    LOGIN_FAILED,
    ACCESS_DENIED,
    SUSPICIOUS_REQUEST
}
