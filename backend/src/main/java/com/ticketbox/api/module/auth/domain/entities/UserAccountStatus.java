package com.ticketbox.api.module.auth.domain.entities;

public enum UserAccountStatus {
    ACTIVE, // Phương thức này đang dùng bình thường
    SUSPENDED, // Tạm dừng / khóa riêng phương thức này
    DELETED // Đã hủy liên kết (soft-deleted)
}