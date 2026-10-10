# Bảng `ticket_types`

> [← `seat_zones`](seat_zones.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`user_ticket_type_counters` →](user_ticket_type_counters.md)

**Mục đích:** Loại vé của concert: giá, tổng số lượng, số đang giữ/đã bán, giới hạn mỗi người và thời gian mở bán.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh loại vé |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` ON DELETE CASCADE | Concert của loại vé |
| `seat_zone_id` | UUID | NOT NULL, FK kép `(seat_zone_id, concert_id)` → `seat_zones(id, concert_id)` ON DELETE CASCADE | Khu vực áp dụng (bắt buộc cùng concert) |
| `name` | VARCHAR(100) | NOT NULL; UNIQUE cùng `concert_id` | Tên loại vé |
| `description` | TEXT | nullable | Mô tả |
| `price` | NUMERIC(12,2) | NOT NULL, CHECK `>= 0` | Giá vé |
| `currency` | VARCHAR(3) | NOT NULL, mặc định `VND` | Đơn vị tiền |
| `total_quantity` | INT | NOT NULL, CHECK `>= 0` | Tổng số vé phát hành |
| `held_quantity` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số vé đang được giữ chỗ (đơn `HELD`) |
| `sold_quantity` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số vé đã bán |
| `max_per_user` | INT | NOT NULL, CHECK `> 0` | Số vé tối đa mỗi người được mua/giữ |
| `sale_start_at` | TIMESTAMPTZ | NOT NULL | Bắt đầu mở bán |
| `sale_end_at` | TIMESTAMPTZ | NOT NULL, CHECK `> sale_start_at` | Kết thúc mở bán |
| `status` | VARCHAR(20) | NOT NULL, mặc định `DRAFT`, CHECK | Trạng thái bán |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**Ràng buộc nghiệp vụ ở mức DB:** `chk_ticket_types_inventory` — `total_quantity >= held_quantity + sold_quantity` (không bán/giữ vượt tồn kho).

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `DRAFT` | Nháp (mặc định) |
| | `ACTIVE` | Đã kích hoạt |
| | `ON_SALE` | Đang mở bán |
| | `SUSPENDED` | Tạm ngưng bán |
| | `CLOSED` | Đã đóng bán |
| | `SOLD_OUT` | Hết vé |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`concerts`](concerts.md) | n–1 | Nhiều loại vé thuộc một concert |
| [`seat_zones`](seat_zones.md) | n–1 | Nhiều loại vé có thể cùng một khu vực |
| [`order_items`](order_items.md) | 1–n | Được mua trong nhiều dòng đơn hàng |
| [`tickets`](tickets.md) | 1–n | Nhiều vé thuộc cùng một loại vé |
| [`users`](users.md) | n–n | Qua [`user_ticket_type_counters`](user_ticket_type_counters.md) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_ticket_types_concert_name` (unique) | `(concert_id, name)` | Tên loại vé duy nhất trong concert |
| `uk_ticket_types_id_concert` (unique) | `(id, concert_id)` | Đích của FK kép từ `tickets` |
| `idx_ticket_types_concert_status` | `(concert_id, status)` | Lọc loại vé theo concert và trạng thái |
| `idx_ticket_types_sale_window` | `(sale_start_at, sale_end_at)` | Tìm theo khung thời gian bán |
| `idx_ticket_types_seat_zone` | `seat_zone_id` | Loại vé theo khu vực |

---

[← `seat_zones`](seat_zones.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`user_ticket_type_counters` →](user_ticket_type_counters.md)
