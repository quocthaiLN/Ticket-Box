# Bảng `payment_events`

> [← `payments`](payments.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`refunds` →](refunds.md)

**Mục đích:** Nhật ký sự kiện thanh toán bất biến (append-only event log) lưu trữ nguyên văn mọi tín hiệu nhận được từ cổng thanh toán (IPN webhook, ReturnUrl redirect, Query DR định kỳ, hoặc tác vụ nội bộ). Chịu trách nhiệm chống xử lý trùng lặp và phục vụ đối soát/điều tra tranh chấp tài chính.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK, mặc định `gen_random_uuid()` | Định danh duy nhất của sự kiện |
| `payment_id` | UUID | nullable, FK → `payments(id)` ON DELETE SET NULL | Giao dịch thanh toán liên quan (có thể NULL nếu IPN không tìm thấy payment tương ứng để vẫn lưu vết điều tra) |
| `provider` | VARCHAR(50) | NOT NULL, CHECK | Cổng thanh toán gửi sự kiện (`VNPAY`, `MOMO`, `STRIPE`, `ALIPAY`) |
| `event_source` | VARCHAR(50) | NOT NULL, CHECK | Kênh phát sinh sự kiện (`IPN`, `RETURN_URL`, `QUERY_API`, `INTERNAL`) |
| `dedupe_key` | VARCHAR(255) | NOT NULL, UNIQUE cùng `provider` (`uk_payment_events_provider_dedupe`) | Khóa chống trùng lặp sự kiện (mã sự kiện của cổng hoặc hash các trường cốt lõi) |
| `event_type` | VARCHAR(100) | nullable | Loại sự kiện (ví dụ: `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `PAYMENT_CANCELLED`) |
| `raw_payload` | JSONB | NOT NULL | Nguyên văn dữ liệu payload nhận được từ cổng |
| `signature_valid` | BOOLEAN | NOT NULL | Cờ xác thực chữ ký số (ghi nhận cả sự kiện sai chữ ký để phục vụ điều tra) |
| `processing_status` | VARCHAR(50) | NOT NULL, mặc định `RECEIVED`, CHECK | Trạng thái xử lý sự kiện trong hệ thống |
| `error_message` | TEXT | nullable | Thông điệp lỗi chi tiết nếu xử lý thất bại |
| `received_at` | TIMESTAMPTZ | NOT NULL, mặc định `CURRENT_TIMESTAMP` | Thời điểm hệ thống tiếp nhận sự kiện |
| `processed_at` | TIMESTAMPTZ | nullable | Thời điểm hoàn tất xử lý nghiệp vụ của sự kiện |

**ENUM / CHECK Constraints**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `provider` | `VNPAY`, `MOMO`, `STRIPE`, `ALIPAY` | Cổng thanh toán phát sinh sự kiện |
| `event_source` | `IPN` | Webhook server-to-server từ cổng gọi tới TicketBox |
| | `RETURN_URL` | Callback từ trình duyệt người dùng khi chuyển hướng về |
| | `QUERY_API` | Tác vụ chủ động truy vấn kết quả giao dịch từ TicketBox sang cổng |
| | `INTERNAL` | Sự kiện do hệ thống tự sinh (ví dụ: phát hiện URL muộn sau timeout) |
| `processing_status` | `RECEIVED` | Sự kiện mới tiếp nhận, đang chờ xử lý |
| | `PROCESSED` | Xử lý nghiệp vụ thành công |
| | `FAILED` | Xử lý gặp lỗi (sai số tiền, xung đột dữ liệu) |
| | `IGNORED` | Bỏ qua không xử lý (chữ ký sai, sự kiện không hợp lệ) |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`payments`](payments.md) | n–1 | Mỗi sự kiện thanh toán có thể gắn với một giao dịch `payments` |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_payment_events_provider_dedupe` (unique) | `(provider, dedupe_key)` | Ràng buộc chống trùng lặp sự kiện tuyệt đối theo từng cổng |
| `idx_payment_events_payment_id` | `payment_id` | Tra cứu toàn bộ lịch sử sự kiện của một giao dịch |
| `idx_payment_events_status` | `(processing_status, received_at)` | Hỗ trợ worker quét các sự kiện chưa xử lý hoặc điều tra sự kiện lỗi |
