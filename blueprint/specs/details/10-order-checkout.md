# Đặc tả: Order Checkout & Thanh toán

## 1. Mô tả

Đặc tả toàn diện vòng đời đơn hàng từ bước giữ vé, khởi tạo giao dịch thanh toán (VNPAY, MoMo), xử lý kết quả Webhook/IPN, đối soát chủ động, retry thanh toán cho đến khi phát hành vé hoặc hết hạn giữ chỗ.

Tài liệu này phản ánh chính xác hành vi mã nguồn đang triển khai trong [`com.ticketbox.api.module.order`](../../../backend/src/main/java/com/ticketbox/api/module/order) và [`com.ticketbox.api.module.payment`](../../../backend/src/main/java/com/ticketbox/api/module/payment).

## 2. Actor / Thành phần tham gia

- **Khán giả (Audience)**: Chọn vé, tạo đơn hàng giữ chỗ, thực hiện thanh toán trên cổng.
- **Ticketing / Catalog Module**: Quản lý tồn kho vé (`ticket_types`) và giới hạn mỗi tài khoản (`user_ticket_type_counters`).
- **Order Module**: Quản lý vòng đời đơn hàng (`orders`, `order_items`), kiểm soát trạng thái giữ vé (`HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`).
- **Payment Module**: Tạo phiên thanh toán, tích hợp cổng (VNPAY, MoMo), điều phối IPN và đối soát bù.
- **Cổng thanh toán (VNPAY, MoMo)**: Tiếp nhận thanh toán của khách hàng, phản hồi ReturnUrl (trình duyệt) và IPN (server-to-server).
- **Schedulers / Workers**:
  - `OrderExpirationScheduler`: Quét và giải phóng các đơn hàng hết hạn giữ vé.
  - `PaymentReconciliationScheduler`: Quét và đối soát các giao dịch đang chờ kết quả hoặc chưa lấy được URL.

## 3. Bảng dữ liệu liên quan

- `orders`: `id`, `user_id`, `status` (`HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`), `total_amount`, `currency`, `hold_expires_at`, `idempotency_key`.
- `order_items`: `id`, `order_id`, `ticket_type_id`, `quantity`, `unit_price`, `total_price`.
- `ticket_types`: `id`, `concert_id`, `held_quantity`, `sold_quantity`, `total_quantity`.
- `user_ticket_type_counters`: `user_id`, `ticket_type_id`, `held_count`, `bought_count`.
- `payments`: `id`, `order_id`, `provider`, `status` (`CREATING`, `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`), `amount`, `checkout_url`, `idempotency_key`, `next_reconcile_at`, `refund_required`.
- `tickets`: Lưu trữ vé điện tử được phát hành sau khi đơn hàng thanh toán thành công.

## 4. Luồng xử lý chi tiết

### 4.1. Tạo đơn hàng và Giữ vé (`POST /orders`)
1. Khán giả chọn các hạng vé và số lượng, gửi request `POST /orders` kèm header `Idempotency-Key`.
2. Backend kiểm tra điều kiện mở bán:
   - Concert đang trong trạng thái `PUBLISHED` và nằm trong khung giờ bán vé (`sale_start_at` <= now <= `sale_end_at`).
   - Kiểm tra tồn kho còn đủ: `sold_quantity + held_quantity + requested <= total_quantity`.
   - Kiểm tra giới hạn mua vé của người dùng (`max_tickets_per_user`): `bought_count + held_count + requested <= max_per_user`.
3. Chạy transaction cập nhật kho:
   - Tăng `held_quantity` trên các `ticket_types`.
   - Tăng `held_count` trên `user_ticket_type_counters`.
   - Tạo bản ghi `orders` với trạng thái `HELD`, gán thời hạn `hold_expires_at = now() + TTL` (mặc định 10 phút).
   - Tạo các `order_items` tương ứng.
4. Trả về thông tin đơn hàng `OrderResponse` cho client.

### 4.2. Khởi tạo thanh toán (`POST /payments`)
1. Client gửi `POST /payments` kèm header `Idempotency-Key` (UUID v4) và body `{ orderId, provider, bankCode, locale }`.
2. Kiểm tra điều kiện đơn hàng trong transaction (khóa bi quan `findByIdForUpdate`):
   - Đơn hàng tồn tại và thuộc về user đang đăng nhập.
   - Trạng thái đơn hàng phải là `HELD`.
   - Thời hạn `hold_expires_at` phải còn hiệu lực (`now < hold_expires_at`).
   - Đơn hàng chưa có payment nào đang `PENDING` hoặc `CREATING`.
   - Đơn hàng chưa từng thanh toán thành công (`SUCCEEDED`).
3. Khởi tạo giao dịch theo cổng:
   - **VNPAY**:
     - Tạo bản ghi `payments` trạng thái `PENDING`.
     - Tự ký URL thanh toán tại chỗ (HMAC-SHA512 với `vnp_TxnRef = payment.id` dạng hex 32 ký tự).
     - Lưu `checkout_url` và commit trong một transaction duy nhất. Trả URL về cho client chuyển hướng.
   - **MoMo**:
     - **Transaction 1**: Tạo bản ghi `payments` trạng thái `CREATING`, chưa có URL, hẹn lịch `next_reconcile_at = now() + 60s`.
     - Gọi MoMo Create API (`/v2/gateway/api/create`) qua `CircuitBreaker` Resilience4j (timeout 30s).
     - **Nếu có payUrl**: Transaction 2 chuyển `payments.status = PENDING`, lưu `checkout_url` và trả URL cho client.
     - **Nếu timeout / 5xx / Circuit breaker mở**: Giữ nguyên trạng thái `CREATING`, URL rỗng. Trả response `{ status: "CREATING" }` để frontend hiển thị màn hình chuẩn bị thanh toán và hẹn scheduler quét bù.
     - **Nếu MoMo từ chối nghiệp vụ (mã lỗi cụ thể)**: Chuyển `payments.status = FAILED`.

### 4.3. Xử lý Callback: ReturnUrl vs IPN Webhook

| Đặc điểm | ReturnUrl (Trình duyệt) | IPN Webhook (Server-to-Server) |
|---|---|---|
| **Kênh gọi** | Trình duyệt chuyển hướng từ cổng về TicketBox | Cổng thanh toán gọi trực tiếp vào backend API |
| **Bản chất** | Chỉ dùng để hiển thị giao diện cho người dùng | **Nguồn sự thật duy nhất** để xác nhận kết quả thanh toán |
| **Hành vi Backend** | Không bao giờ thay đổi trạng thái đơn hàng hay phát hành vé từ tham số URL | Xác thực chữ ký số, khóa bi quan DB, cập nhật đơn hàng và phát hành vé |
| **Xử lý Frontend** | Hiển thị màn hình "Đang kiểm tra kết quả...", bắt đầu polling `GET /payments/{id}` hoặc `GET /orders/{id}` | Không liên quan trực tiếp đến frontend |

### 4.4. Xử lý IPN Webhook và Cập nhật Đơn hàng
1. Gateway verify chữ ký số của payload nhận được.
2. Khóa dòng payment bằng `paymentRepository.findByIdForUpdate(paymentId)`.
3. Kiểm tra số tiền khớp với `payment.amount`.
4. **Nếu payment đã ở trạng thái kết thúc (`SUCCEEDED`, `FAILED`, `CANCELLED`)**:
   - Nếu cùng kết quả: Bỏ qua, phản hồi thành công idempotent cho cổng (VNPAY `02`, MoMo `204`).
   - Nếu kết quả mâu thuẫn: Không ghi đè DB, log lỗi xung đột và phản hồi mã lỗi tương ứng.
5. **Nếu payment đang `PENDING` hoặc `CREATING`**:
   - Lưu raw webhook payload, thời điểm nhận và đánh dấu `webhook_signature_valid = true`.
   - **Trường hợp thanh toán THÀNH CÔNG**:
     - Khóa dòng order và kiểm tra thời hạn giữ vé: `order.status == HELD && now < order.hold_expires_at`.
     - **Nếu HOLD CÒN HẠN**:
       - Chuyển `payment.status = SUCCEEDED`, cập nhật `paid_at = now()`.
       - Gọi `orderInventoryService.settlePaidOrder`: Chuyển `order.status = CONFIRMED`, chuyển `held_quantity` thành `sold_quantity`, chuyển `held_count` thành `bought_count`.
       - Phát hành vé điện tử `ticketIssuer.issueTickets(event)`.
     - **Nếu HOLD ĐÃ HẾT HẠN (Late Payment)**:
       - Chuyển `payment.status = SUCCEEDED` (ghi nhận thực tế tiền đã thu).
       - **Tuyệt đối không chuyển order sang CONFIRMED và không phát hành vé**.
       - Đánh dấu `payment.refund_required = true` và ghi nhận `failure_reason = "REFUND_REQUIRED: paid after hold expiry..."`.
   - **Trường hợp thanh toán THẤT BẠI hoặc HỦY**:
     - Chuyển `payment.status = FAILED` hoặc `CANCELLED`, lưu lý do thất bại.
     - `order` vẫn giữ nguyên trạng thái `HELD` để người dùng có thể thử lại nếu còn trong thời hạn hold.
   - Xóa lịch `next_reconcile_at = null` và commit transaction.

### 4.5. Cơ chế Thử lại Thanh toán (Payment Retry)
- Khi một lượt thanh toán bị `FAILED` hoặc `CANCELLED`, người dùng không cần đặt lại đơn hàng:
  1. Người dùng có thể chọn lại phương thức thanh toán và gọi `POST /payments` với `Idempotency-Key` mới cho cùng `orderId`.
  2. Điều kiện: `order.status` vẫn là `HELD` và `hold_expires_at` chưa hết hạn.
  3. Hệ thống tạo một bản ghi `payments` mới độc lập cho cùng `order_id`.
  4. Ràng buộc: Một order có thể có nhiều attempts thanh toán, nhưng chỉ có tối đa một attempt chuyển thành `SUCCEEDED`. Tuyệt đối không cho tạo payment mới nếu order đã hết hạn hoặc đã được xác nhận.

### 4.6. Cơ chế Tự động Hết hạn Đơn hàng (Order Expiration)
- `OrderExpirationScheduler` chạy định kỳ (mỗi 10 giây):
  1. Quét các đơn hàng `orders` có `status = HELD` và `hold_expires_at < now()`.
  2. Với mỗi đơn hết hạn, gọi `orderInventoryService.releaseHoldOrder`:
     - Chuyển `orders.status = EXPIRED`.
     - Hoàn lại số lượng tồn kho `ticket_types.held_quantity`.
     - Giảm `user_ticket_type_counters.held_count`.
  3. Nếu sau đó IPN thanh toán mới về cho đơn hàng này, hệ thống sẽ rơi vào trường hợp "Late Payment" (khoản tiền được ghi nhận `SUCCEEDED` kèm `refund_required = true`, không khôi phục vé đã nhả).

### 4.7. Cơ chế Đối soát Bù (Payment Reconciliation)
- `PaymentReconciliationScheduler` chạy định kỳ (mỗi 30 giây):
  1. Quét các payment có `next_reconcile_at <= now()` và trạng thái `CREATING` hoặc `PENDING`.
  2. **Đối với MoMo đang `CREATING` (khi order còn hạn)**: Thử gọi lại MoMo Create API với cùng requestId để lấy `payUrl`.
  3. **Đối với payment đang `PENDING` (thiếu IPN)**: Gọi API Query DR của VNPAY hoặc Query API của MoMo để chủ động hỏi trạng thái giao dịch.
  4. Kết quả truy vấn được đưa vào xử lý qua cùng một logic hội tụ `applyVerifiedOutcome` như khi nhận IPN thật, đảm bảo tính nhất quán tuyệt đối.

## 5. Tiêu chí chấp nhận (Acceptance Criteria)

- Đơn hàng giữ vé chính xác số lượng và tự động nhả kho khi hết hạn `hold_expires_at`.
- Phân biệt rõ ràng giữa kết quả tức thời của VNPAY (ký tại chỗ) và MoMo (bảo vệ bằng Circuit Breaker).
- ReturnUrl không làm thay đổi trạng thái thanh toán hay đơn hàng.
- Webhook/IPN xử lý idempotent, khóa bi quan, không phát hành vé 2 lần.
- IPN về sau khi hold hết hạn không được phát vé, ghi nhận cờ hoàn tiền `refund_required = true`.
- Cho phép thử lại thanh toán với payment attempt mới khi lượt trước thất bại và hold còn hạn.
