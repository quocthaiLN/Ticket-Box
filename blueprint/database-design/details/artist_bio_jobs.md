# Bảng `artist_bio_jobs`

> [← `tickets`](tickets.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`notifications` →](notifications.md)

**Mục đích:** Tác vụ nền tạo tiểu sử nghệ sĩ bằng AI từ file nguồn. Từ V4, bảng hỗ trợ **thử lại** và **cơ chế thuê xử lý (lease)** để phục hồi khi worker bị treo hoặc chết giữa chừng.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh tác vụ |
| `concert_id` | UUID | NOT NULL, FK → `concerts(id)` ON DELETE CASCADE | Concert cần tạo bio |
| `requested_by` | UUID | nullable, FK → `users(id)` | Người yêu cầu |
| `status` | VARCHAR(20) | NOT NULL, mặc định `PENDING`, CHECK | Trạng thái tác vụ |
| `source_file_url` | TEXT | NOT NULL | Đường dẫn file nguồn |
| `extracted_text` | TEXT | nullable | Văn bản trích xuất từ file nguồn |
| `generated_bio` | TEXT | nullable | Tiểu sử do AI tạo |
| `error_message` | TEXT | nullable | Thông báo lỗi |
| `attempts` **(V4)** | INTEGER | NOT NULL, mặc định 0, CHECK `>= 0` | Số lần đã thử xử lý |
| `next_attempt_at` **(V4)** | TIMESTAMPTZ | nullable | Thời điểm sớm nhất được thử lại |
| `processing_token` **(V4)** | UUID | nullable | Token của worker đang xử lý (để chỉ worker đó được ghi kết quả) |
| `lease_until` **(V4)** | TIMESTAMPTZ | nullable | Thời điểm hết hạn thuê xử lý; quá hạn mà vẫn `PROCESSING` thì tác vụ có thể được nhận lại |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `PENDING` | Chờ xử lý (mặc định) |
| | `PROCESSING` | Đang xử lý |
| | `DONE` | Hoàn tất |
| | `FAILED` | Thất bại |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`concerts`](concerts.md) | n–1 | Một concert có thể có nhiều tác vụ tạo bio |
| [`users`](users.md) | n–1 | Nhiều tác vụ do một người yêu cầu (tùy chọn) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `idx_abj_concert_status` | `(concert_id, status)` | Tác vụ theo concert và trạng thái |
| `idx_abj_status_created` | `(status, created_at)` | Quét hàng đợi theo trạng thái và thời gian tạo |
| `idx_abj_pending_due` **(V4)** | `(next_attempt_at, updated_at)` — *partial*, `WHERE status = 'PENDING'` | Tìm nhanh tác vụ `PENDING` đến hạn xử lý |
| `idx_abj_processing_lease` **(V4)** | `lease_until` — *partial*, `WHERE status = 'PROCESSING'` | Tìm nhanh tác vụ `PROCESSING` đã hết hạn lease |

---

[← `tickets`](tickets.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`notifications` →](notifications.md)
