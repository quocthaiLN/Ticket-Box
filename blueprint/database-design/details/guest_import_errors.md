# Bảng `guest_import_errors`

> [← `guest_list`](guest_list.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`artist_bio_jobs` →](artist_bio_jobs.md)

**Mục đích:** Ghi lại chi tiết từng dòng lỗi khi nhập CSV để người dùng xem và sửa.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh bản ghi lỗi |
| `job_id` | UUID | NOT NULL, FK → `guest_import_jobs(id)` ON DELETE CASCADE | Tác vụ nhập chứa lỗi |
| `row_number` | INT | NOT NULL, CHECK `> 0` | Số thứ tự dòng trong file |
| `raw_data` | JSONB | nullable | Dữ liệu gốc của dòng lỗi |
| `error_code` | VARCHAR(100) | NOT NULL | Mã lỗi |
| `error_message` | TEXT | NOT NULL | Mô tả lỗi |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm ghi nhận |

**ENUM:** Không có (`error_code` là chuỗi tự do).

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`guest_import_jobs`](guest_import_jobs.md) | n–1 | Nhiều lỗi thuộc một tác vụ |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_guest_import_errors_job_row_code` (unique) | `(job_id, row_number, error_code)` | Một dòng có thể có nhiều loại lỗi nhưng mỗi loại ghi một lần |
| `idx_guest_import_errors_job_id` | `job_id` | Các lỗi của một tác vụ |

---

[← `guest_list`](guest_list.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`artist_bio_jobs` →](artist_bio_jobs.md)
