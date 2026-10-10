# Bảng `notifications`

> [← `artist_bio_jobs`](artist_bio_jobs.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`audit_logs` →](audit_logs.md)

**Mục đích:** Nhật ký và hàng đợi thông báo gửi đến người dùng qua nhiều kênh.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh thông báo |
| `user_id` | UUID | nullable, FK → `users(id)` | Người nhận |
| `concert_id` | UUID | nullable, FK → `concerts(id)` | Concert liên quan |
| `ticket_id` | UUID | nullable, FK → `tickets(id)` | Vé liên quan |
| `channel` | VARCHAR(20) | NOT NULL, CHECK | Kênh gửi |
| `type` | VARCHAR(50) | NOT NULL, CHECK | Loại thông báo |
| `status` | VARCHAR(20) | NOT NULL, mặc định `PENDING`, CHECK | Trạng thái gửi |
| `payload` | JSONB | NOT NULL | Nội dung/dữ liệu thông báo |
| `attempts` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số lần đã thử gửi |
| `error_message` | TEXT | nullable | Lỗi gửi |
| `sent_at` | TIMESTAMPTZ | nullable | Thời điểm gửi thành công |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `channel` | `APP` | Thông báo trong ứng dụng |
| | `EMAIL` | Email |
| | `SMS` | Tin nhắn SMS |
| | `ZALO` | Zalo |
| `type` | `ORDER_HELD` | Đơn đang giữ chỗ |
| | `ORDER_CONFIRMED` | Đơn đã xác nhận |
| | `ORDER_CANCELLED` | Đơn đã hủy |
| | `TICKET_ISSUED` | Vé đã phát hành |
| | `CONCERT_REMINDER` | Nhắc lịch concert |
| | `EVENT_REMINDER` | Nhắc sự kiện (xem [mục 5 trong file tổng quan](../database-design.md#5-ghi-chú--điểm-cần-lưu-ý)) |
| | `SYSTEM_ALERT` | Cảnh báo hệ thống |
| `status` | `PENDING` | Chờ gửi (mặc định) |
| | `SENT` | Đã gửi |
| | `FAILED` | Gửi thất bại |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`users`](users.md) | n–1 | Nhiều thông báo cho một người dùng (tùy chọn) |
| [`concerts`](concerts.md) | n–1 | Nhiều thông báo liên quan một concert (tùy chọn) |
| [`tickets`](tickets.md) | n–1 | Nhiều thông báo liên quan một vé (tùy chọn) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `idx_notifications_status_created` | `(status, created_at)` | Quét hàng đợi thông báo chờ gửi |
| `idx_notifications_user_created` | `(user_id, created_at)` | Hộp thư của người dùng |
| `idx_notifications_concert_status` | `(concert_id, status)` | Thông báo theo concert |
| `idx_notifications_ticket_id` | `ticket_id` | Thông báo theo vé |

---

[← `artist_bio_jobs`](artist_bio_jobs.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`audit_logs` →](audit_logs.md)
