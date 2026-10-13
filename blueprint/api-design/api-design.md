# TicketBox — API Design Documentation

Tài liệu thiết kế kiến trúc và đặc tả chi tiết toàn bộ các REST API trong hệ thống TicketBox.

---

## 1. Quy ước nền tảng

- [`base-api.md`](details/base-api.md): Quy ước chung cho tất cả API (Base URL, HTTP headers, authentication JWT, response envelope `ApiResponse`, cursor/offset pagination, mã lỗi RFC 7807/9457 `ProblemDetail`, idempotency và rate limiting).

---

## 2. Danh mục API chi tiết theo Bounded Context

| STT | Tài liệu API | Bounded Context / Phân hệ | Mô tả chức năng chính |
|---|---|---|---|
| 1 | [`auth-rbac-api.md`](details/auth-rbac-api.md) | Auth & RBAC | Đăng ký, đăng nhập JWT, refresh token, Redis denylist, quản lý người dùng và gán role cho Admin |
| 2 | [`catalog-api.md`](details/catalog-api.md) | Catalog & Concerts | Xem danh mục sự kiện công khai, quản lý concert, sơ đồ chỗ ngồi (`seat_zones`) và loại vé (`ticket_types`) |
| 3 | [`order-checkout-api.md`](details/order-checkout-api.md) | Orders & Reservation | Đặt chỗ tạm giữ vé, đếm quota `user_ticket_type_counters`, thanh toán và hủy đơn |
| 4 | [`inventory-api.md`](details/inventory-api.md) | Inventory | Tra cứu số lượng vé khả dụng (`available_quantity`), tồn kho thời gian thực |
| 5 | [`e-ticket-api.md`](details/e-ticket-api.md) | E-Ticket & QR Check-in | Phát hành vé điện tử, tạo mã HMAC QR code, quét vé online/offline |
| 6 | [`artist-bio-api.md`](details/artist-bio-api.md) | Artist Bio & AI | Upload hồ sơ nghệ sĩ dạng PDF và tiến trình bất đồng bộ AI trích xuất tiểu sử |
| 7 | [`notification-api.md`](details/notification-api.md) | Notifications | Gửi email thông báo, mã OTP, xác nhận đơn hàng qua RabbitMQ |
| 8 | [`audit-log-api.md`](details/audit-log-api.md) | Audit Logging | Tra cứu và điều tra nhật ký kiểm toán hệ thống dành riêng cho Quản trị viên (Admin) |

---

## 3. Nguyên tắc bảo mật và phân quyền

- Toàn bộ các API trong nhóm Quản trị (`/admin/**`) bắt buộc phải có vai trò phù hợp (`ADMIN` hoặc `ORGANIZER`).
- Riêng API kiểm toán [`audit-log-api.md`](details/audit-log-api.md) (`/admin/audit-logs`) được bảo vệ nghiêm ngặt: chỉ duy nhất vai trò `ADMIN` được phép truy cập và không cung cấp bất kỳ API ghi/sửa/xóa nào ra bên ngoài.
