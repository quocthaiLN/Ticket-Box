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

    // --- Order / Inventory ---
    HOLD_ORDER,
    RELEASE_ORDER,          // order HELD -> EXPIRED, hoàn kho; nguồn duy nhất: OrderExpirationScheduler

    // --- Payment ---
    CREATE_PAYMENT,
    COMPLETE_PAYMENT,       // payment SUCCESS + order CONFIRMED + phát vé
    FAIL_PAYMENT,           // metadata.new_status: FAILED | CANCELLED
    EXPIRE_PAYMENT,
    RECORD_LATE_PAYMENT,    // payment SUCCESS sau khi hold hết hạn, không phát vé

    // --- Refund ---
    REQUEST_REFUND,
    COMPLETE_REFUND,
    FAIL_REFUND,

    // =========================================================================
    // NHÓM 2: SỰ KIỆN THẤT BẠI CẦN ĐIỀU TRA (Propagation.REQUIRES_NEW)
    // Ghi trong transaction riêng biệt để tồn tại độc lập kể cả khi nghiệp vụ cha rollback.
    // Phải được gọi trước khi ném exception ra ngoài.
    // Nuốt lỗi an toàn kèm ghi log hệ thống nếu DB audit gặp sự cố.
    // =========================================================================
    LOGIN_FAILED,
    ACCESS_DENIED,
    SUSPICIOUS_REQUEST,

    // --- Payment investigative ---
    PAYMENT_WEBHOOK_REJECTED, // metadata.reason: INVALID_SIGNATURE | AMOUNT_MISMATCH | PAYMENT_NOT_FOUND
    PAYMENT_RESULT_CONFLICT
}
