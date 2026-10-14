# Bảng `refunds`

> [← `payment_events`](payment_events.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`tickets` →](tickets.md)

**Mục đích:** Quản lý các giao dịch hoàn tiền độc lập cho thanh toán của đơn hàng (hoàn tiền tự động khi IPN đến muộn sau khi hold hết hạn, hoặc hoàn tiền khi ban tổ chức hủy concert / khán giả đổi trả vé). Đảm bảo tính idempotent và theo dõi kết quả gọi API hoàn tiền sang các cổng thanh toán (VNPAY, MoMo).

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK, mặc định `gen_random_uuid()` | Định danh duy nhất của lệnh hoàn tiền |
| `payment_id` | UUID | NOT NULL, FK → `payments(id)` ON DELETE RESTRICT | Giao dịch thanh toán gốc được hoàn tiền |
| `amount` | NUMERIC(12,2) | NOT NULL, CHECK `> 0` | Số tiền hoàn lại (VND). Tổng tiền các refund thành công không vượt quá `payments.amount` |
| `currency` | VARCHAR(3) | NOT NULL, mặc định `VND` | Đơn vị tiền tệ |
| `idempotency_key` | VARCHAR(128) | NOT NULL, UNIQUE (`uk_refunds_idempotency_key`) | Khóa chống tạo lệnh hoàn tiền trùng lặp (ví dụ: `LATE_REFUND_<merchant_txn_ref>`) |
| `status` | VARCHAR(50) | NOT NULL, mặc định `REQUESTED`, CHECK | Trạng thái của lệnh hoàn tiền |
| `provider_refund_id` | VARCHAR(255) | nullable | Mã giao dịch hoàn tiền do cổng cấp sau khi nhận lệnh |
| `reason` | TEXT | nullable | Lý do hoàn tiền (phục vụ đối soát và báo cáo) |
| `requested_by` | UUID | NOT NULL | Định danh người/hệ thống yêu cầu hoàn tiền (user ID hoặc System Actor UUID) |
| `failure_code` | VARCHAR(100) | nullable | Mã lỗi nếu cổng từ chối lệnh hoàn tiền |
| `failure_message` | TEXT | nullable | Mô tả chi tiết nguyên nhân hoàn tiền thất bại |
| `requested_at` | TIMESTAMPTZ | NOT NULL, mặc định `CURRENT_TIMESTAMP` | Thời điểm phát sinh yêu cầu hoàn tiền |
| `completed_at` | TIMESTAMPTZ | nullable | Thời điểm cổng xác nhận hoàn tiền thành công hoặc thất bại cuối cùng |

**ENUM / CHECK Constraints**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `status` | `REQUESTED` | Lệnh hoàn tiền đã được tạo trong hệ thống, đang chờ gửi sang cổng |
| | `PROCESSING` | Đã gửi lệnh sang cổng, đang chờ kết quả đối soát bất đồng bộ |
| | `SUCCESS` | Cổng xác nhận đã hoàn tiền thành công cho tài khoản người mua |
| | `FAILED` | Lệnh hoàn tiền bị cổng từ chối hoặc thất bại kỹ thuật |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`payments`](payments.md) | n–1 | Một payment có thể có một hoặc nhiều lệnh hoàn tiền (cho phép hoàn tiền từng phần hoặc hoàn tiền toàn bộ) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_refunds_idempotency_key` (unique) | `idempotency_key` | Đảm bảo tính idempotent, không bao giờ tạo 2 lệnh hoàn tiền trùng nhau |
| `idx_refunds_payment_id` | `payment_id` | Tra cứu lịch sử hoàn tiền của một giao dịch thanh toán |
| `idx_refunds_status` | `(status, requested_at)` | Hỗ trợ worker quét các lệnh hoàn tiền đang `REQUESTED` hoặc `PROCESSING` để thử lại / đối soát |
