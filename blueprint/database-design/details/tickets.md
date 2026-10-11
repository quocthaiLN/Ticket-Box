# Bảng `tickets`

> [← `payments`](payments.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`artist_bio_jobs` →](artist_bio_jobs.md)

**Mục đích:** Vé điện tử đã phát hành sau khi thanh toán, kèm mã QR (lưu dạng băm và chữ ký) và thông tin check-in.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh vé |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` | Đơn hàng phát sinh vé |
| `order_item_id` | UUID | NOT NULL, FK → `order_items(id)` | Dòng đơn phát sinh vé |
| `user_id` | UUID | NOT NULL, FK → `users(id)` | Chủ sở hữu vé |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` | Concert của vé |
| `ticket_type_id` | UUID | NOT NULL, FK kép `(ticket_type_id, concert_id)` → `ticket_types(id, concert_id)` | Loại vé (bắt buộc cùng concert) |
| `seat_zone_id` | UUID | NOT NULL, FK kép `(seat_zone_id, concert_id)` → `seat_zones(id, concert_id)` | Khu vực (bắt buộc cùng concert) |
| `qr_token_hash` | VARCHAR(255) | NOT NULL, UNIQUE | Giá trị băm của token QR (không lưu token gốc) |
| `qr_payload` | JSONB | nullable | Dữ liệu mã hóa trong QR |
| `qr_signature` | TEXT | nullable | Chữ ký xác thực QR |
| `status` | VARCHAR(20) | NOT NULL, mặc định `ISSUED`, CHECK | Trạng thái vé |
| `issued_at` | TIMESTAMPTZ | NOT NULL | Thời điểm phát hành |
| `checked_in_at` | TIMESTAMPTZ | nullable | Thời điểm check-in |
| `checked_in_by` | UUID | nullable, FK → `users(id)` | Nhân viên soát vé đã check-in |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `ISSUED` | Đã phát hành, chưa dùng (mặc định) |
| | `CHECKED_IN` | Đã check-in |
| | `REFUNDED` | Đã hoàn tiền |
| | `INVALIDATED` | Bị vô hiệu hóa |
| | `CANCELLED` | Đã hủy |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`orders`](orders.md) | n–1 | Một đơn có nhiều vé |
| [`order_items`](order_items.md) | n–1 | Một dòng đơn sinh ra nhiều vé |
| [`users`](users.md) | n–1 | Chủ sở hữu; ngoài ra `checked_in_by` là n–1 tới người soát vé |
| [`concerts`](concerts.md) | n–1 | Nhiều vé thuộc một concert |
| [`ticket_types`](ticket_types.md) | n–1 | Nhiều vé thuộc một loại vé |
| [`seat_zones`](seat_zones.md) | n–1 | Nhiều vé thuộc một khu vực |
| [`notifications`](notifications.md) | 1–n | Thông báo liên quan đến vé (nullable) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_tickets_qr_token_hash` (unique) | `qr_token_hash` | Tra cứu vé khi quét QR |
| `idx_tickets_user_concert_status` | `(user_id, concert_id, status)` | Vé của người dùng theo concert |
| `idx_tickets_user_ticket_type_status` | `(user_id, ticket_type_id, status)` | Đếm vé người dùng theo loại vé |
| `idx_tickets_ticket_type_status` | `(ticket_type_id, status)` | Thống kê theo loại vé |
| `idx_tickets_order_id` | `order_id` | Vé của một đơn |
| `idx_tickets_order_item_id` | `order_item_id` | Vé của một dòng đơn |

---

[← `payments`](payments.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`artist_bio_jobs` →](artist_bio_jobs.md)
