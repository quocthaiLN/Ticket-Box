# Bảng `audit_logs`

> [← `notifications`](notifications.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính)

**Mục đích:** Nhật ký hành động trong hệ thống để truy vết (ai làm gì, trên đối tượng nào, từ đâu).

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh bản ghi |
| `actor_user_id` | UUID | nullable, FK → `users(id)` | Người thực hiện (NULL nếu do hệ thống) |
| `action` | VARCHAR(100) | NOT NULL | Hành động |
| `entity_type` | VARCHAR(100) | NOT NULL | Loại đối tượng bị tác động |
| `entity_id` | VARCHAR(100) | nullable | Định danh đối tượng (lưu dạng chuỗi, **không** có FK) |
| `metadata` | JSONB | nullable | Dữ liệu bổ sung |
| `ip_address` | VARCHAR(45) | nullable | Địa chỉ IP (đủ chỗ cho IPv6) |
| `user_agent` | TEXT | nullable | User agent của client |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm ghi nhận |

**ENUM:** Không có (`action`, `entity_type` là chuỗi tự do).

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`users`](users.md) | n–1 | Nhiều bản ghi do một người dùng thực hiện (tùy chọn) |
| Bảng khác | — | Liên kết **logic** qua `(entity_type, entity_id)`, không có khóa ngoại |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `idx_audit_logs_entity` | `(entity_type, entity_id)` | Xem lịch sử của một đối tượng |
| `idx_audit_logs_actor_created` | `(actor_user_id, created_at)` | Xem hoạt động của một người theo thời gian |

---

[← `notifications`](notifications.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính)
