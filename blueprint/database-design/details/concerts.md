# Bảng `concerts`

> [← `user_accounts`](user_accounts.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`seat_zones` →](seat_zones.md)

**Mục đích:** Sự kiện/concert do ban tổ chức tạo.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh concert |
| `organizer_id` | UUID | NOT NULL, FK → `users(id)` ON DELETE CASCADE | Ban tổ chức sở hữu |
| `title` | VARCHAR(255) | NOT NULL | Tên concert |
| `slug` | VARCHAR(255) | NOT NULL, UNIQUE | Đường dẫn thân thiện (URL) |
| `venue` | VARCHAR(255) | NOT NULL | Địa điểm tổ chức |
| `description` | TEXT | nullable | Mô tả |
| `artist_name` | VARCHAR(255) | NOT NULL | Tên nghệ sĩ |
| `artist_bio` | TEXT | nullable | Tiểu sử nghệ sĩ |
| `starts_at` | TIMESTAMPTZ | NOT NULL | Thời điểm bắt đầu |
| `ends_at` | TIMESTAMPTZ | NOT NULL, CHECK `ends_at > starts_at` | Thời điểm kết thúc |
| `status` | VARCHAR(20) | NOT NULL, mặc định `DRAFT`, CHECK | Trạng thái concert |
| `cover_image_url` | TEXT | nullable | Ảnh bìa |
| `seat_map_url` | TEXT | nullable | Ảnh/sơ đồ chỗ ngồi |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `DRAFT` | Bản nháp (mặc định) |
| | `PUBLISHED` | Đã công bố |
| | `CANCELLED` | Đã hủy |
| | `COMPLETED` | Đã diễn ra xong |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`users`](users.md) | n–1 | Mỗi concert thuộc một ban tổ chức |
| [`seat_zones`](seat_zones.md) | 1–n | Một concert có nhiều khu vực chỗ ngồi |
| [`ticket_types`](ticket_types.md) | 1–n | Một concert có nhiều loại vé |
| [`orders`](orders.md) | 1–n | Một concert có nhiều đơn hàng |
| [`tickets`](tickets.md) | 1–n | Một concert có nhiều vé đã phát hành |
| [`guest_import_jobs`](guest_import_jobs.md), [`guest_list`](guest_list.md) | 1–n | Khách mời và các lần nhập CSV của concert |
| [`artist_bio_jobs`](artist_bio_jobs.md) | 1–n | Các tác vụ tạo bio của concert |
| [`notifications`](notifications.md) | 1–n | Thông báo liên quan đến concert (nullable) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_concerts_slug` (unique) | `slug` | Tra cứu theo URL |
| `idx_concerts_slug` | `slug` | Trùng chức năng với unique index |
| `idx_concerts_title` | `title` | Tìm theo tên |
| `idx_concerts_status_starts` | `(status, starts_at)` | Liệt kê concert theo trạng thái, sắp theo ngày diễn |
| `idx_concerts_organizer_id` | `organizer_id` | Concert của một ban tổ chức |

---

[← `user_accounts`](user_accounts.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`seat_zones` →](seat_zones.md)
