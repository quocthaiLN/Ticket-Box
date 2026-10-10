# Bảng `orders`

> [← `user_ticket_type_counters`](user_ticket_type_counters.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`order_items` →](order_items.md)

**Mục đích:** Đơn hàng của người dùng cho một concert. Khi tạo đơn, vé được **giữ chỗ** (`HELD`) đến `hold_expires_at`; sau đó đơn được xác nhận, hủy hoặc hết hạn.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh đơn hàng |
| `user_id` | UUID | NOT NULL, FK → `users(id)` | Người đặt |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` | Concert được đặt |
| `idempotency_key` | VARCHAR(128) | NOT NULL, UNIQUE | Khóa chống tạo trùng đơn khi gửi lại request |
| `status` | VARCHAR(20) | NOT NULL, mặc định `HELD`, CHECK | Trạng thái đơn |
| `total_amount` | NUMERIC(12,2) | NOT NULL, CHECK `>= 0` | Tổng tiền |
| `currency` | VARCHAR(3) | NOT NULL, mặc định `VND` | Đơn vị tiền |
| `hold_expires_at` | TIMESTAMPTZ | nullable | Hạn giữ chỗ |
| `confirmed_at` | TIMESTAMPTZ | nullable | Thời điểm xác nhận |
| `cancelled_at` | TIMESTAMPTZ | nullable | Thời điểm hủy |
| `expired_at` | TIMESTAMPTZ | nullable | Thời điểm hết hạn |
| `cancelled_reason` | TEXT | nullable | Lý do hủy |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `HELD` | Đang giữ chỗ, chờ thanh toán (mặc định) |
| | `CONFIRMED` | Đã xác nhận (thanh toán thành công) |
| | `CANCELLED` | Đã hủy |
| | `EXPIRED` | Hết hạn giữ chỗ |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`users`](users.md) | n–1 | Nhiều đơn thuộc một người dùng |
| [`concerts`](concerts.md) | n–1 | Nhiều đơn thuộc một concert |
| [`order_items`](order_items.md) | 1–n | Một đơn có nhiều dòng |
| [`payments`](payments.md) | 1–n | Một đơn có thể có nhiều lần/giao dịch thanh toán |
| [`tickets`](tickets.md) | 1–n | Một đơn phát hành nhiều vé |
| [`ticket_types`](ticket_types.md) | n–n | Qua [`order_items`](order_items.md) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_orders_idempotency_key` (unique) | `idempotency_key` | Chống trùng |
| `idx_orders_user_status_created` | `(user_id, status, created_at)` | Lịch sử đơn của người dùng |
| `idx_orders_concert_status` | `(concert_id, status)` | Thống kê/quản lý đơn theo concert |
| `idx_orders_hold_expires_at` | `hold_expires_at` | Quét các đơn giữ chỗ đã hết hạn |

---

[← `user_ticket_type_counters`](user_ticket_type_counters.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`order_items` →](order_items.md)
