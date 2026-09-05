package com.ticketbox.api.module.auth.domain.entities;

public enum UserStatus {
    PENDING, // Đã đăng ký, chờ xác thực email/OTP
    ACTIVE, // Đang hoạt động bình thường
    SUSPENDED, // Bị tạm dừng / cấm (Admin phạt, điều tra vi phạm)
    DELETED // Đã xóa (Soft-delete / Hủy tài khoản)
}