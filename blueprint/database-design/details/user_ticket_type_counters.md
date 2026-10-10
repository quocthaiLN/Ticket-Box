# Bảng `user_ticket_type_counters`

> [← `ticket_types`](ticket_types.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`orders` →](orders.md)

**Mục đích:** Đếm số vé mỗi người dùng đang giữ và đã mua theo từng loại vé, phục vụ kiểm tra giới hạn `ticket_types.max_per_user`.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `user_id` | UUID | PK (kép), FK → `users(id)` ON DELETE CASCADE | Người dùng |
| `ticket_type_id` | UUID | PK (kép), FK → `ticket_types(id)` ON DELETE CASCADE | Loại vé |
| `held_quantity` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số vé đang giữ chỗ |
| `paid_quantity` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số vé đã thanh toán |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM:** Không có.

**Quan hệ:** Bảng trung gian **n–n** giữa [`users`](users.md) và [`ticket_types`](ticket_types.md) (mỗi cặp người dùng–loại vé tối đa một dòng).

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| PK | `(user_id, ticket_type_id)` | Truy cập theo cặp |
| `idx_uttc_ticket_type_id` | `ticket_type_id` | Tra theo loại vé |

---

[← `ticket_types`](ticket_types.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`orders` →](orders.md)
