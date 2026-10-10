# Bảng `payments`

> [← `order_items`](order_items.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`tickets` →](tickets.md)

**Mục đích:** Giao dịch thanh toán của đơn hàng qua cổng thanh toán; lưu dữ liệu gửi đi, webhook nhận về và thông tin phục vụ đối soát/hoàn tiền.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh giao dịch |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` ON DELETE CASCADE | Đơn hàng được thanh toán |
| `provider` | VARCHAR(50) | NOT NULL, CHECK | Cổng thanh toán |
| `provider_transaction_id` | VARCHAR(255) | nullable; UNIQUE cùng `provider` | Mã giao dịch phía cổng (có thể chưa có khi mới tạo) |
| `idempotency_key` | VARCHAR(128) | NOT NULL, UNIQUE | Khóa chống tạo trùng giao dịch |
| `amount` | NUMERIC(12,2) | NOT NULL, CHECK `> 0` | Số tiền thanh toán |
| `currency` | VARCHAR(3) | NOT NULL, mặc định `VND` | Đơn vị tiền |
| `status` | VARCHAR(20) | NOT NULL, mặc định `PENDING`, CHECK | Trạng thái thanh toán |
| `checkout_url` | TEXT | nullable | Đường dẫn thanh toán trả về cho người dùng |
| `provider_payload` | JSONB | nullable | Dữ liệu trao đổi với cổng khi tạo giao dịch |
| `webhook_payload` | JSONB | nullable | Nội dung webhook cổng gửi về |
| `webhook_received_at` | TIMESTAMPTZ | nullable | Thời điểm nhận webhook |
| `webhook_signature_valid` | BOOLEAN | nullable | Chữ ký webhook có hợp lệ hay không |
| `paid_at` | TIMESTAMPTZ | nullable | Thời điểm thanh toán thành công |
| `failure_reason` | TEXT | nullable | Lý do thất bại |
| `next_reconcile_at` **(V3)** | TIMESTAMPTZ | nullable | Thời điểm cần đối soát lại giao dịch với cổng; NULL = không cần đối soát |
| `refund_required` **(V3)** | BOOLEAN | NOT NULL, mặc định `FALSE` | Cờ đánh dấu giao dịch cần hoàn tiền (ví dụ tiền đã thu nhưng đơn không còn hợp lệ) |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

**ENUM**

| Cột | Giá trị | Ý nghĩa |
|---|---|---|
| `provider` | `VNPAY`, `MOMO`, `STRIPE`, `ALIPAY` | Cổng thanh toán được hỗ trợ |
| `status` | `CREATING` **(V3)** | Đang khởi tạo giao dịch (chưa có kết quả từ cổng) |
| | `PENDING` | Chờ thanh toán (mặc định) |
| | `SUCCEEDED` | Thành công |
| | `FAILED` | Thất bại |
| | `CANCELLED` | Đã hủy |
| | `EXPIRED` | Hết hạn |
| | `REFUNDED` | Đã hoàn tiền |

> V3 xóa và tạo lại `chk_payments_status` để thêm giá trị `CREATING`. Cột `status` vẫn mặc định `PENDING`.

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`orders`](orders.md) | n–1 | Một đơn có thể có nhiều giao dịch thanh toán (ví dụ thử lại) |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_payments_idempotency_key` (unique) | `idempotency_key` | Chống trùng |
| `uk_payments_provider_tx` (unique) | `(provider, provider_transaction_id)` | Một mã giao dịch của cổng chỉ ghi nhận một lần; các dòng có `provider_transaction_id` NULL không bị coi là trùng nhau |
| `idx_payments_order_status` | `(order_id, status)` | Thanh toán của đơn theo trạng thái |
| `idx_payments_status_created` | `(status, created_at)` | Quét giao dịch theo trạng thái và thời gian |
| `idx_payments_next_reconcile_at` **(V3)** | `next_reconcile_at` — *partial*, `WHERE next_reconcile_at IS NOT NULL` | Chỉ lập chỉ mục các giao dịch đang chờ đối soát |
| `idx_payments_refund_required` **(V3)** | `refund_required` — *partial*, `WHERE refund_required = TRUE` | Chỉ lập chỉ mục các giao dịch cần hoàn tiền |

---

[← `order_items`](order_items.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`tickets` →](tickets.md)
