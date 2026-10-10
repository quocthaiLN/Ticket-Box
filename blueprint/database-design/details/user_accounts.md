# Bảng `user_accounts`

> [← `users`](users.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`concerts` →](concerts.md)

**Mục đích:** Lưu các phương thức đăng nhập của một người dùng (mật khẩu nội bộ `LOCAL` hoặc nhà cung cấp danh tính bên ngoài).

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh tài khoản đăng nhập |
| `user_id` | UUID | NOT NULL, FK → `users(id)` ON DELETE CASCADE | Người dùng sở hữu |
| `password_hash` | TEXT | nullable; bắt buộc không rỗng nếu `provider = 'LOCAL'` | Mật khẩu đã băm (chỉ dùng cho đăng nhập nội bộ) |
| `provider` | TEXT | NOT NULL | Nhà cung cấp đăng nhập (ví dụ `LOCAL`; các giá trị khác không bị giới hạn bởi DB) |
| `provider_user_id` | TEXT | NOT NULL | Định danh của người dùng phía nhà cung cấp |
| `user_account_status` | VARCHAR(20) | NOT NULL, mặc định `ACTIVE`, CHECK | Trạng thái phương thức đăng nhập |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |
| `deleted_at` | TIMESTAMPTZ | nullable | Xóa mềm |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `user_account_status` | `ACTIVE` | Đang dùng (mặc định) |
| | `SUSPENDED` | Tạm khóa |
| | `DELETED` | Đã xóa |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`users`](users.md) | n–1 | Nhiều tài khoản đăng nhập thuộc một người dùng |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_user_accounts_provider_provider_user_id` (unique) | `(provider, provider_user_id)` | Một danh tính ngoài chỉ gắn với một tài khoản |
| `idx_user_accounts_user_id` | `user_id` | Tra cứu tài khoản theo người dùng |

---

[← `users`](users.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`concerts` →](concerts.md)
