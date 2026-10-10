# Bảng `users`

> [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`user_accounts` →](user_accounts.md)

**Mục đích:** Hồ sơ người dùng và vai trò trong hệ thống. Thông tin đăng nhập nằm ở `user_accounts`.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh người dùng |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE, CHECK chứa `@` | Email (duy nhất toàn hệ thống) |
| `full_name` | VARCHAR(255) | NOT NULL | Họ tên |
| `phone` | VARCHAR(20) | UNIQUE, nullable | Số điện thoại (không bắt buộc, nếu có thì không trùng) |
| `role` | VARCHAR(20) | NOT NULL, mặc định `AUDIENCE`, CHECK | Vai trò người dùng |
| `status` | VARCHAR(20) | NOT NULL, mặc định `ACTIVE`, CHECK | Trạng thái tài khoản |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật gần nhất |
| `deleted_at` | TIMESTAMPTZ | nullable | Thời điểm xóa mềm (NULL = chưa xóa) |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `role` | `AUDIENCE` | Khán giả/người mua vé (mặc định) |
| | `ORGANIZER` | Ban tổ chức, tạo và quản lý concert |
| | `CHECKER` | Nhân viên soát vé |
| | `ADMIN` | Quản trị hệ thống |
| `status` | `ACTIVE` | Đang hoạt động (mặc định) |
| | `PENDING` | Chờ kích hoạt/xác thực |
| | `SUSPENDED` | Bị tạm khóa |
| | `DELETED` | Đã xóa |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`user_accounts`](user_accounts.md) | 1–n | Một người dùng có nhiều phương thức đăng nhập |
| [`concerts`](concerts.md) | 1–n | Một ban tổ chức tạo nhiều concert (`concerts.organizer_id`) |
| [`orders`](orders.md) | 1–n | Một người dùng có nhiều đơn hàng |
| [`tickets`](tickets.md) | 1–n | Là chủ sở hữu vé (`tickets.user_id`); còn là người soát vé qua `tickets.checked_in_by` |
| [`ticket_types`](ticket_types.md) | n–n | Qua bảng [`user_ticket_type_counters`](user_ticket_type_counters.md) |
| [`guest_import_jobs`](guest_import_jobs.md), [`guest_list`](guest_list.md), [`artist_bio_jobs`](artist_bio_jobs.md), [`notifications`](notifications.md), [`audit_logs`](audit_logs.md) | 1–n | Là người tải lên / soát vé / yêu cầu / nhận thông báo / thực hiện hành động |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_users_email` (unique) | `email` | Tự động tạo index |
| `uk_users_phone` (unique) | `phone` | Tự động tạo index |
| `idx_users_email` | `email` | Trùng chức năng với unique index ở trên |
| `idx_users_phone` | `phone` | Trùng chức năng với unique index ở trên |
| `idx_users_role` | `role` | Lọc theo vai trò |
| `idx_users_status` | `status` | Lọc theo trạng thái |

---

[Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`user_accounts` →](user_accounts.md)
