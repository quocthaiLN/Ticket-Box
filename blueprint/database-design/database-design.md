# TicketBox — Thiết kế cơ sở dữ liệu

> Tài liệu được tổng hợp từ các migration: `V1__init_schema.sql`, `V3__payment_resilience.sql`, `V4__artist_bio_recovery.sql`.
> File này là **tổng quan**. Mô tả chi tiết từng bảng nằm trong thư mục [`details/`](details/).
> Hệ quản trị: **PostgreSQL** (extension `pgcrypto` để sinh UUID bằng `gen_random_uuid()`).
> Các cột/ràng buộc/index thêm ở V3, V4 được đánh dấu **(V3)** / **(V4)**.
> Lưu ý: không có file `V2` trong phạm vi tài liệu này.

---

## Mục lục

1. [Quy ước chung](#1-quy-ước-chung)
2. [Phụ lục: Danh sách bảng chính](#2-phụ-lục-danh-sách-bảng-chính)
3. [Sơ đồ quan hệ tổng quan](#3-sơ-đồ-quan-hệ-tổng-quan)
4. [Tổng hợp hành vi xóa (ON DELETE)](#4-tổng-hợp-hành-vi-xóa-on-delete)
5. [Ghi chú & điểm cần lưu ý](#5-ghi-chú--điểm-cần-lưu-ý)

---

## 1. Quy ước chung

| Nội dung | Quy ước |
|---|---|
| Khóa chính | `id UUID` mặc định `gen_random_uuid()` (trừ `user_ticket_type_counters` dùng khóa chính kép) |
| Thời gian | Dùng `TIMESTAMPTZ`; `created_at`, `updated_at` mặc định `CURRENT_TIMESTAMP` |
| Tiền tệ | `NUMERIC(12,2)`, đơn vị tiền mặc định `VND` (`currency VARCHAR(3)`) |
| Enum | Không dùng kiểu `ENUM` của PostgreSQL; dùng `VARCHAR` + `CHECK (... IN (...))` |
| Đặt tên | PK/FK: `fk_<bảng>_<đối tượng>`; Unique: `uk_...`; Check: `chk_...`; Index: `idx_...` |
| Soft delete | Chỉ `users` và `user_accounts` có `deleted_at` |
| Idempotency | `orders.idempotency_key` và `payments.idempotency_key` đều UNIQUE để chống tạo trùng khi client gửi lại request |

---

## 2. Phụ lục: Danh sách bảng chính

| # | Nhóm | Bảng | Chức năng |
|---|---|---|---|
| 1 | Người dùng | [`users`](details/users.md) | Thông tin người dùng và vai trò (khán giả, ban tổ chức, nhân viên soát vé, admin) |
| 2 | Người dùng | [`user_accounts`](details/user_accounts.md) | Phương thức đăng nhập của người dùng (mật khẩu nội bộ hoặc nhà cung cấp bên ngoài) |
| 3 | Danh mục | [`concerts`](details/concerts.md) | Sự kiện/concert do ban tổ chức tạo |
| 4 | Danh mục | [`seat_zones`](details/seat_zones.md) | Khu vực chỗ ngồi của một concert, kèm sức chứa |
| 5 | Kho vé | [`ticket_types`](details/ticket_types.md) | Loại vé: giá, số lượng, thời gian mở bán, gắn với một khu vực |
| 6 | Kho vé | [`user_ticket_type_counters`](details/user_ticket_type_counters.md) | Bộ đếm số vé mỗi người đang giữ/đã mua theo loại vé (để áp giới hạn `max_per_user`) |
| 7 | Đặt vé | [`orders`](details/orders.md) | Đơn hàng; vòng đời giữ chỗ → xác nhận/hủy/hết hạn |
| 8 | Đặt vé | [`order_items`](details/order_items.md) | Các dòng của đơn hàng (loại vé, số lượng, đơn giá) |
| 9 | Thanh toán | [`payments`](details/payments.md) | Giao dịch thanh toán của đơn hàng qua cổng thanh toán, kèm dữ liệu webhook và đối soát |
| 10 | Vé | [`tickets`](details/tickets.md) | Vé điện tử đã phát hành, mã QR, trạng thái check-in |
| 11 | Khách mời | [`guest_import_jobs`](details/guest_import_jobs.md) | Tác vụ nhập danh sách khách mời từ file CSV |
| 12 | Khách mời | [`guest_list`](details/guest_list.md) | Danh sách khách mời của concert và trạng thái check-in |
| 13 | Khách mời | [`guest_import_errors`](details/guest_import_errors.md) | Chi tiết từng dòng lỗi khi nhập CSV |
| 14 | AI | [`artist_bio_jobs`](details/artist_bio_jobs.md) | Tác vụ nền tạo tiểu sử nghệ sĩ bằng AI từ file nguồn |
| 15 | Thông báo | [`notifications`](details/notifications.md) | Nhật ký/hàng đợi thông báo (app, email, SMS, Zalo) |
| 16 | Kiểm toán | [`audit_logs`](details/audit_logs.md) | Nhật ký hành động để truy vết |

---

## 3. Tổng hợp hành vi xóa (ON DELETE)

| FK | Hành vi |
|---|---|
| `user_accounts.user_id`, `concerts.organizer_id`, `user_ticket_type_counters.user_id` → `users` | CASCADE |
| `seat_zones`, `ticket_types`, `guest_import_jobs`, `guest_list`, `artist_bio_jobs` → `concerts` | CASCADE |
| `ticket_types (seat_zone_id, concert_id)` → `seat_zones` | CASCADE |
| `user_ticket_type_counters.ticket_type_id` → `ticket_types` | CASCADE |
| `order_items.order_id`, `payments.order_id` → `orders` | CASCADE |
| `guest_import_errors.job_id` → `guest_import_jobs` | CASCADE |
| `guest_list.import_job_id` → `guest_import_jobs` | SET NULL |
| `orders`, `tickets`, `notifications`, `audit_logs`, `order_items.ticket_type_id` và các FK còn lại | Không khai báo (mặc định `NO ACTION` — chặn xóa nếu còn bản ghi tham chiếu) |

Hệ quả: dữ liệu giao dịch (đơn hàng, vé, thông báo, nhật ký) được bảo vệ khỏi việc xóa nhầm bản ghi cha như `users` hay `concerts`.

---

## 4. Ghi chú & điểm cần lưu ý

1. **Trạng thái hủy trong `concerts.status`:** Đã thống nhất chỉ dùng `CANCELLED`, đồng bộ với các bảng khác (`orders`, `payments`, `tickets`, `guest_list`).
2. **`ticket_types.status` có cả `ACTIVE` và `ON_SALE`**, và **`notifications.type` có cả `CONCERT_REMINDER` và `EVENT_REMINDER`**: các giá trị này có ý nghĩa gần nhau; schema không phân biệt rõ, cần theo quy ước của tầng ứng dụng.
4. **FK kép bảo vệ tính nhất quán theo concert:** `ticket_types → seat_zones`, `tickets → ticket_types/seat_zones`, `guest_list → seat_zones` đều tham chiếu `(id, concert_id)`, nên không thể gắn khu vực/loại vé của concert này vào dữ liệu của concert khác.
5. **UNIQUE với cột nullable:** trong PostgreSQL, nhiều dòng có giá trị NULL không vi phạm UNIQUE; điều này áp dụng cho `payments(provider, provider_transaction_id)` và `guest_list(concert_id, code)`.
7. **Hai nhóm cơ chế phục hồi (V3, V4):** `payments` có đối soát (`next_reconcile_at`) và hoàn tiền (`refund_required`) dùng *partial index* để việc quét chỉ chạm vào các dòng cần xử lý.