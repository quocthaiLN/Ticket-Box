# Bảng `seat_zones`

> [← `concerts`](concerts.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`ticket_types` →](ticket_types.md)

**Mục đích:** Khu vực chỗ ngồi (ví dụ VIP, GA, CAT1) của một concert; mỗi khu vực có sức chứa riêng.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh khu vực |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` ON DELETE CASCADE | Concert chứa khu vực |
| `code` | VARCHAR(50) | NOT NULL; UNIQUE cùng `concert_id` | Mã khu vực (duy nhất trong một concert) |
| `name` | VARCHAR(100) | NOT NULL | Tên hiển thị |
| `description` | TEXT | nullable | Mô tả |
| `capacity` | INT | NOT NULL, CHECK `> 0` | Sức chứa của khu vực |
| `svg_path` | TEXT | nullable | Đường vẽ SVG của khu vực trên sơ đồ chỗ ngồi |
| `sort_order` | INT | NOT NULL, mặc định 0 | Thứ tự hiển thị |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM:** Không có.

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`concerts`](concerts.md) | n–1 | Nhiều khu vực thuộc một concert |
| [`ticket_types`](ticket_types.md) | 1–n | Một khu vực có thể có nhiều loại vé |
| [`tickets`](tickets.md) | 1–n | Vé gắn với khu vực |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_seat_zones_concert_code` (unique) | `(concert_id, code)` | Mã khu vực duy nhất theo concert |
| `uk_seat_zones_id_concert` (unique) | `(id, concert_id)` | Làm đích cho **FK kép** từ `ticket_types`, `tickets` để đảm bảo khu vực cùng concert |
| `idx_seat_zones_concert_id` | `concert_id` | Khu vực của một concert |
| `idx_seat_zones_code` | `code` | Tìm theo mã |
| `idx_seat_zones_name` | `name` | Tìm theo tên |
| `idx_seat_zones_concert_sort` | `(concert_id, sort_order)` | Liệt kê khu vực theo thứ tự hiển thị |

---

[← `concerts`](concerts.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`ticket_types` →](ticket_types.md)
