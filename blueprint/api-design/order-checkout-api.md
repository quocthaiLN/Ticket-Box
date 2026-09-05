# TicketBox — Order Checkout API Design

Tài liệu tóm tắt các API của Order Checkout theo code hiện tại.

- Order API tạo order `HELD`, quản lý quota, chi tiết và hủy giữ chỗ.
- Payment API là bước riêng để tạo giao dịch và checkout URL.
- Worker hoặc dịch vụ nội bộ xử lý order hết hạn.
- Organizer và Admin có API tra cứu danh sách order phục vụ vận hành.

## 1. Audience Order API

Các API sau yêu cầu đăng nhập và role `AUDIENCE` hoặc `ADMIN`.

| Method | Endpoint | Chức năng | Ghi chú chính |
| --- | --- | --- | --- |
| `GET` | `/concerts/{concert_id}/my-ticket-quota` | Lấy hạn mức mua vé của user theo từng ticket type. | Trả số tối đa, số đang giữ, số đã thanh toán và số còn được mua; phản hồi `no-store`. |
| `POST` | `/orders` | Tạo order và giữ vé. | Yêu cầu `Idempotency-Key`; chỉ tạo order `HELD`, không tạo checkout URL; trả `201`. |
| `GET` | `/orders/{order_id}` | Lấy chi tiết order của user hiện tại. | Gồm item, payment mới nhất và vé đã phát hành; chỉ chủ sở hữu được đọc; phản hồi `no-store`. |
| `POST` | `/orders/{order_id}/cancel` | Hủy order còn giữ chỗ. | Chỉ hủy order `HELD` của chính user; trả tồn kho và quota trong cùng transaction. |

### Header của API tạo order

| Header | Yêu cầu |
| --- | --- |
| `Authorization` | Access token của user có role `AUDIENCE` hoặc `ADMIN`. |
| `Idempotency-Key` | Bắt buộc, không rỗng, tối đa 128 ký tự và được phân vùng theo user. |

Request tạo order chỉ nhận `concert_id` và danh sách `items`; mỗi item gồm `ticket_type_id` cùng `quantity` nguyên dương. Giá, tổng tiền và hạn giữ vé do server quyết định.

## 2. Payment API liên quan

Payment là module riêng nhưng là bước kế tiếp trực tiếp của checkout.

| Method | Endpoint | Chức năng | Ghi chú chính |
| --- | --- | --- | --- |
| `POST` | `/orders/{order_id}/payments` | Tạo một payment attempt và checkout URL cho order. | Yêu cầu đăng nhập, role `AUDIENCE` hoặc `ADMIN` và `Idempotency-Key` thuộc scope `payments`. |

Order API không tự gọi endpoint này. Web App nhận order `HELD`, sau đó chủ động tạo payment attempt. Việc return URL, webhook, đối soát và phát hành vé thuộc Payment module.

## 3. Internal Order API

| Method | Endpoint | Chức năng | Ghi chú chính |
| --- | --- | --- | --- |
| `POST` | `/internal/orders/{order_id}/expire` | Chuyển order `HELD` sang `EXPIRED` và trả tồn kho/quota. | Xử lý có tính lặp an toàn nếu order không còn `HELD`; order không tồn tại trả `404`. |

Endpoint này dành cho worker hoặc dịch vụ nội bộ, không dành cho client công khai.

## 4. Organizer và Admin Order API

Endpoint sau yêu cầu đăng nhập và role `ORGANIZER` hoặc `ADMIN`.

| Method | Endpoint | Chức năng | Ghi chú chính |
| --- | --- | --- | --- |
| `GET` | `/admin/orders` | Tra cứu danh sách order phục vụ vận hành. | Sắp xếp theo `created_at`, `id` giảm dần; hỗ trợ cursor và tối đa 100 dòng theo code repository. |

### Query danh sách order

| Query | Chức năng |
| --- | --- |
| `concert_id` | Lọc order theo concert. |
| `status` | Lọc theo trạng thái order. |
| `user_id` | Lọc theo user đặt vé. |
| `from`, `to` | Lọc theo thời điểm tạo order. |
| `limit` | Mặc định 20, repository giới hạn tối đa 100. |
| `cursor` | Cursor base64url tạo từ `created_at` và `id`; response trả `next_cursor` cùng `has_more`. |

## 5. Quy tắc nghiệp vụ chính

| Quy tắc | Yêu cầu |
| --- | --- |
| Trạng thái ban đầu | `POST /orders` chỉ tạo order `HELD`; Payment module mới chuyển order sang `CONFIRMED` khi thanh toán thành công. |
| Điều kiện giữ vé | Mọi ticket type phải thuộc cùng concert, đang `ON_SALE`, nằm trong khung giờ bán, đủ tồn kho và không làm user vượt `max_per_user`. |
| Giữ nhiều loại vé | Các ticket type được lock theo thứ tự ổn định; một item lỗi làm toàn bộ transaction rollback. |
| Thời hạn giữ | Server tự tính `hold_expires_at`; mặc định hiện tại là 900 giây. Client không được truyền hạn giữ. |
| Hủy và hết hạn | Chỉ order `HELD` được release; thao tác giảm cả tồn kho held và quota held, sau đó vô hiệu hóa snapshot inventory của Catalog. |
| Giá order | Đơn giá và thành tiền lấy từ PostgreSQL tại thời điểm giữ vé, không lấy từ client. |

## 6. Cơ chế bảo vệ và tính nhất quán

| Cơ chế | Triển khai hiện tại |
| --- | --- |
| Rate limit | `POST /orders` có fixed window 300 request/60 giây/IP trước auth và 30 request/60 giây/user sau auth. |
| Admission control | Redis semaphore giới hạn số transaction tạo order đồng thời theo concert; mặc định 10 lease/concert, hết slot trả `429` thay vì xếp hàng. |
| Idempotency | Redis lưu/replay response theo scope, user, key và fingerprint; unique database bảo vệ trường hợp tranh chấp hiếm. |
| Transaction | Tạo hold cập nhật order, items, tồn kho và quota cùng nhau; lỗi tranh chấp/deadlock phù hợp được thử lại tối đa 3 lần. |
| Cache | Chi tiết order và quota dùng `no-store`; các luồng HTTP thay đổi hold xóa snapshot inventory của concert. |

## 7. Mã lỗi chính

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| `400` | `INVALID_CHECKOUT_REQUEST` | Thiếu hoặc sai dữ liệu tạo order; cursor danh sách không hợp lệ. |
| `400` | `MISSING_IDEMPOTENCY_KEY` / `IDEMPOTENCY_KEY_REUSED` | Thiếu key hoặc dùng lại key cho request có nội dung khác. |
| `401` / `403` | `UNAUTHORIZED` / `FORBIDDEN` | Chưa đăng nhập hoặc không có role phù hợp. |
| `403` / `404` | `ORDER_ACCESS_DENIED` / `ORDER_NOT_FOUND` | Không phải chủ order hoặc order không tồn tại. |
| `409` | `TICKET_SOLD_OUT` / `PER_USER_LIMIT_EXCEEDED` | Không đủ tồn kho hoặc vượt giới hạn mua của user. |
| `409` | `ORDER_ALREADY_FINALIZED` | Cố hủy order không còn ở trạng thái `HELD`. |
| `422` | `TICKET_TYPE_NOT_ON_SALE` / `SALE_WINDOW_CLOSED` | Loại vé chưa mở bán, đã đóng hoặc nằm ngoài khung giờ bán. |
| `429` / `503` | `RATE_LIMITED`, `ORDER_CAPACITY_REACHED` / `ORDER_ADMISSION_UNAVAILABLE` | Vượt giới hạn request, hết admission slot hoặc Redis admission không khả dụng. |