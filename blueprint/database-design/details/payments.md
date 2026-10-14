# Bảng `payments`

> [← `order_items`](order_items.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`payment_events` →](payment_events.md) · [`refunds` →](refunds.md)

**Mục đích:** Lưu trữ các phiên/lượt giao dịch thanh toán (attempts) của đơn hàng qua các cổng thanh toán (VNPAY, MoMo). Mỗi lượt thanh toán sở hữu một mã tham chiếu `merchant_txn_ref` duy nhất và số phiên bản `version` để kiểm soát đồng thời lạc quan.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK, mặc định `gen_random_uuid()` | Định danh duy nhất của giao dịch thanh toán |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` ON DELETE CASCADE | Đơn hàng được thanh toán |
| `provider` | VARCHAR(50) | NOT NULL, CHECK | Cổng thanh toán (`VNPAY`, `MOMO`, `STRIPE`, `ALIPAY`) |
| `merchant_txn_ref` **(V4)** | VARCHAR(100) | NOT NULL, UNIQUE (`uk_payments_merchant_txn_ref`) | Mã tham chiếu duy nhất hệ thống sinh trước khi gọi cổng (ví dụ: `vnp_TxnRef`, `orderId` MoMo) |
| `provider_transaction_id` | VARCHAR(255) | nullable; UNIQUE cùng `provider` (`uk_payments_provider_tx`) | Mã giao dịch phía cổng cấp sau khi thanh toán thành công |
| `idempotency_key` | VARCHAR(128) | NOT NULL, UNIQUE (`uk_payments_idempotency_key`) | Khóa chống tạo trùng giao dịch từ phía client |
| `amount` | NUMERIC(12,2) | NOT NULL, CHECK `> 0` | Số tiền thanh toán (VND) |
| `currency` | VARCHAR(3) | NOT NULL, mặc định `VND` | Đơn vị tiền tệ |
| `status` | VARCHAR(20) | NOT NULL, mặc định `INITIATED`, CHECK | Trạng thái thanh toán |
| `checkout_url` | TEXT | nullable | Đường dẫn thanh toán trả về từ cổng để người dùng mở |
| `failure_code` **(V4)** | VARCHAR(100) | nullable | Mã lỗi chi tiết khi giao dịch thất bại hoặc bị hủy |
| `failure_message` **(V4)** | TEXT | nullable | Mô tả lý do thất bại phục vụ đối soát và hiển thị |
| `version` **(V4)** | BIGINT | NOT NULL, mặc định `0` | Số phiên bản phục vụ Khóa lạc quan (Optimistic Locking) |
| `paid_at` | TIMESTAMPTZ | nullable | Thời điểm thanh toán thành công được cổng xác nhận |
| `next_reconcile_at` **(V3)** | TIMESTAMPTZ | nullable | Thời điểm cần đối soát lại giao dịch với cổng; NULL = không cần đối soát |
| `refund_required` **(V3)** | BOOLEAN | NOT NULL, mặc định `FALSE` | Cờ đánh dấu giao dịch cần hoàn tiền (tiền đã thu nhưng đơn hết hạn) |
| `created_at` | TIMESTAMPTZ | NOT NULL, mặc định `CURRENT_TIMESTAMP` | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | NOT NULL, mặc định `CURRENT_TIMESTAMP` | Thời điểm cập nhật cuối cùng |

**ENUM / CHECK Constraints**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `provider` | `VNPAY`, `MOMO`, `STRIPE`, `ALIPAY` | Cổng thanh toán được hỗ trợ |
| `status` | `INITIATED` **(V4)** | Đã khởi tạo bản ghi trong hệ thống, đang xin URL từ cổng |
| | `PENDING` | Đã có URL thanh toán hợp lệ; đang chờ người dùng thao tác |
| | `SUCCESS` **(V4)** | Thanh toán thành công (cổng đã xác nhận thu tiền) |
| | `FAILED` | Giao dịch bị từ chối hoặc thất bại |
| | `CANCELLED` | Người dùng chủ động hủy giao dịch trên trang cổng |
| | `EXPIRED` | Phiên thanh toán hết hạn mà không nhận được tiền |
| | `REFUNDED` | Giao dịch đã được hoàn trả lại tiền cho người dùng |

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`orders`](orders.md) | n–1 | Một đơn hàng có thể có nhiều lượt thanh toán thử lại (`payments`), nhưng chỉ có tối đa một lượt `SUCCESS` |
| [`payment_events`](payment_events.md) | 1–n | Một payment có nhiều sự kiện webhook/IPN/query đối soát liên quan |
| [`refunds`](refunds.md) | 1–n | Một payment có thể có một hoặc nhiều lệnh hoàn tiền (khi hold hết hạn hoặc hủy vé) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_payments_merchant_txn_ref` (unique) **(V4)** | `merchant_txn_ref` | Tham chiếu định danh độc nhất trên toàn hệ thống |
| `uk_payments_idempotency_key` (unique) | `idempotency_key` | Chống tạo trùng từ client |
| `uk_payments_provider_tx` (unique) | `(provider, provider_transaction_id)` | Mỗi mã giao dịch cổng chỉ ghi nhận 1 lần |
| `idx_payments_order_status` | `(order_id, status)` | Tra cứu các lần thanh toán theo đơn hàng |
| `idx_payments_status_created` | `(status, created_at)` | Quét và thống kê giao dịch theo trạng thái |
| `idx_payments_next_reconcile_at` **(V3)** | `next_reconcile_at` | Partial index (`WHERE next_reconcile_at IS NOT NULL`) cho worker đối soát |
