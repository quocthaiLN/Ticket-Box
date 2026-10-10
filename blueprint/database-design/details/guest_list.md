# Bảng `guest_list`

> [← `guest_import_jobs`](guest_import_jobs.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`guest_import_errors` →](guest_import_errors.md)

**Mục đích:** Danh sách khách mời của concert (không mua vé) và trạng thái check-in của họ.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh khách mời |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` ON DELETE CASCADE | Concert được mời |
| `seat_zone_id` | UUID | nullable, FK kép `(seat_zone_id, concert_id)` → `seat_zones(id, concert_id)` | Khu vực dành cho khách (nếu có; phải cùng concert) |
| `import_job_id` | UUID | nullable, FK → `guest_import_jobs(id)` ON DELETE SET NULL | Tác vụ nhập CSV đã tạo ra dòng này |
| `full_name` | VARCHAR(255) | NOT NULL | Họ tên |
| `phone` | VARCHAR(20) | NOT NULL; UNIQUE cùng `concert_id` | Số điện thoại (duy nhất trong concert) |
| `email` | VARCHAR(255) | nullable | Email |
| `code` | VARCHAR(100) | nullable; UNIQUE cùng `concert_id` | Mã khách mời (duy nhất trong concert nếu có) |
| `status` | VARCHAR(20) | NOT NULL, mặc định `INVITED`, CHECK | Trạng thái khách mời |
| `checked_in_at` | TIMESTAMPTZ | nullable | Thời điểm check-in |
| `checked_in_by` | UUID | nullable, FK → `users(id)` | Người soát vé thực hiện check-in |
| `note` | TEXT | nullable | Ghi chú |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `INVITED` | Đã mời, chưa đến (mặc định) |
| | `CHECKED_IN` | Đã check-in |
| | `CANCELLED` | Đã hủy lời mời |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`concerts`](concerts.md) | n–1 | Nhiều khách mời thuộc một concert |
| [`seat_zones`](seat_zones.md) | n–1 | Nhiều khách mời có thể cùng một khu vực (tùy chọn) |
| [`guest_import_jobs`](guest_import_jobs.md) | n–1 | Nhiều khách mời do cùng một lần nhập (tùy chọn) |
| [`users`](users.md) | n–1 | Qua `checked_in_by` (người soát vé) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_guest_list_concert_phone` (unique) | `(concert_id, phone)` | Một số điện thoại chỉ được mời một lần mỗi concert |
| `uk_guest_list_concert_code` (unique) | `(concert_id, code)` | Mã khách mời duy nhất theo concert; các dòng `code` NULL không bị coi là trùng |
| `idx_guest_list_concert_status` | `(concert_id, status)` | Lọc khách mời theo trạng thái |
| `idx_guest_list_phone` | `phone` | Tra cứu theo số điện thoại khi check-in |

---

[← `guest_import_jobs`](guest_import_jobs.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`guest_import_errors` →](guest_import_errors.md)
