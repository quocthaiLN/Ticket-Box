# Bảng `guest_import_jobs`

> [← `tickets`](tickets.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`guest_list` →](guest_list.md)

**Mục đích:** Theo dõi tác vụ nhập danh sách khách mời từ file CSV cho một concert.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh tác vụ |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` ON DELETE CASCADE | Concert nhận danh sách |
| `uploaded_by` | UUID | NOT NULL, FK → `users(id)` | Người tải file lên |
| `file_url` | TEXT | NOT NULL | Đường dẫn file CSV |
| `status` | VARCHAR(20) | NOT NULL, mặc định `PENDING`, CHECK | Trạng thái xử lý |
| `total_rows` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Tổng số dòng trong file |
| `success_rows` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số dòng nhập thành công |
| `error_rows` | INT | NOT NULL, mặc định 0, CHECK `>= 0` | Số dòng lỗi |
| `started_at` | TIMESTAMPTZ | nullable | Bắt đầu xử lý |
| `completed_at` | TIMESTAMPTZ | nullable | Hoàn tất xử lý |
| `error_message` | TEXT | nullable | Lỗi tổng quát của tác vụ |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `PENDING` | Chờ xử lý (mặc định) |
| | `PROCESSING` | Đang xử lý |
| | `DONE` | Hoàn tất, không lỗi |
| | `PARTIAL` | Hoàn tất một phần (có dòng thành công và có dòng lỗi) |
| | `FAILED` | Thất bại |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`concerts`](concerts.md) | n–1 | Nhiều lần nhập cho một concert |
| [`users`](users.md) | n–1 | Nhiều tác vụ do một người tải lên |
| [`guest_list`](guest_list.md) | 1–n | Một tác vụ tạo ra nhiều khách mời |
| [`guest_import_errors`](guest_import_errors.md) | 1–n | Một tác vụ có nhiều dòng lỗi |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `idx_guest_import_jobs_concert_status` | `(concert_id, status)` | Tác vụ nhập theo concert và trạng thái |

---

[← `tickets`](tickets.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`guest_list` →](guest_list.md)
