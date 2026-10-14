# Đặc tả: Payment Idempotency / Chống thanh toán & xử lý trùng

## 1. Mô tả

Đặc tả cơ chế Idempotency cho luồng tạo thanh toán (`POST /payments`) và xử lý Webhook/IPN của VNPAY & MoMo, đảm bảo:
- Client bấm thanh toán nhiều lần hoặc mạng chập chờn không tạo nhiều giao dịch trùng ngoài ý muốn.
- Cổng thanh toán gửi IPN nhiều lần hoặc gửi lại sau timeout không phát hành vé hai lần và không ghi đè kết quả mâu thuẫn.

Tài liệu này phản ánh chính xác hành vi mã nguồn đang triển khai trong [`com.ticketbox.api.module.payment`](../../../backend/src/main/java/com/ticketbox/api/module/payment).

## 2. Actor / Thành phần tham gia

- **Khán giả (Audience Client)**: Gửi request thanh toán kèm HTTP Header `Idempotency-Key`.
- **Payment Controller & Service**: Xác thực key, tính toán request fingerprint, điều phối transaction và cổng thanh toán.
- **Redis Idempotency Store (`IdempotencyService`)**: Lưu trạng thái xử lý (`PROCESSING`), khóa phân tán và cache kết quả phản hồi (`CreatePaymentResponse`).
- **PostgreSQL Database (`payments`)**: Lưu dữ liệu giao dịch với các ràng buộc `UNIQUE` tầng dữ liệu bền vững.
- **VNPAY / MoMo Gateway & IPN**: Nhận lệnh tạo thanh toán, gửi Webhook/IPN thông báo kết quả giao dịch.

## 3. Bảng dữ liệu và Ràng buộc liên quan

- Bảng `payments`:
  - `id` (UUID, PK): Khóa chính, đồng thời được dùng làm `vnp_TxnRef` (dưới dạng hex 32 ký tự không dấu gạch nối) và MoMo `orderId`/`requestId`.
  - `idempotency_key` (VARCHAR(128), NOT NULL, UNIQUE: `uk_payments_idempotency_key`): Khóa idempotency từ client.
  - `provider` và `provider_transaction_id` (VARCHAR(255), UNIQUE: `uk_payments_provider_tx`): Đảm bảo một mã giao dịch từ phía cổng chỉ được gán duy nhất.
  - `status` (`CREATING`, `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`).
  - `amount`, `currency`, `checkout_url`.
  - `webhook_payload` (JSONB), `webhook_received_at`, `webhook_signature_valid`.
  - `next_reconcile_at`: Thời điểm chạy worker đối soát chủ động khi thiếu IPN hoặc tạo URL chưa hoàn tất.
  - `refund_required` (BOOLEAN): Đánh dấu giao dịch đã thu tiền thành công sau khi hold hết hạn, cần hoàn tiền thủ công.

## 4. Cơ chế Idempotency khi tạo thanh toán (`POST /payments`)

### 4.1. Quy tắc Header & Request Fingerprint
1. Client **bắt buộc** truyền header `Idempotency-Key` định dạng UUID v4. Nếu thiếu trả về `400 MISSING_IDEMPOTENCY_KEY`, nếu sai format trả về `400 INVALID_IDEMPOTENCY_KEY`.
2. Hệ thống tính `fingerprint` (SHA-256) của request dựa trên các trường:
   `currentUser.id | request.orderId | request.provider | bankCode | locale`
3. Redis key được quản lý dưới dạng: `idempotency:payment:{idempotencyKey}`, TTL là **24 giờ**.

### 4.2. Luồng xử lý tại `IdempotencyService`
- **Request mới tinh**:
  1. Redis SETNX key với trạng thái `PROCESSING`, TTL 60 giây.
  2. Thực thi khởi tạo payment.
  3. Khi hoàn tất, cập nhật Redis key với trạng thái `COMPLETED`, kèm response body (`CreatePaymentResponse`) và TTL 24 giờ.
- **Request đang xử lý (`PROCESSING`)**:
  - Nếu client gửi lại cùng key trong khi request trước chưa xong, trả về `409 CONFLICT` (`IDEMPOTENCY_IN_PROGRESS`).
- **Request đã hoàn thành (`COMPLETED`)**:
  - So khớp `fingerprint` hiện tại với fingerprint đã lưu.
  - Nếu khớp: Trả về kết quả đã cache trong Redis (đọc lại trạng thái mới nhất từ DB để đảm bảo URL hoặc status mới nhất).
  - Nếu không khớp (cùng key nhưng khác user, khác orderId hoặc khác provider): Ném lỗi `400 BAD_REQUEST` (`IDEMPOTENCY_KEY_REUSED`).
- **Fallback khi Redis mất dữ liệu**:
  - Kiểm tra trong DB bằng `paymentRepository.findByIdempotencyKey(idempotencyKey)`. Nếu tìm thấy và khớp thông tin đơn/user, trả lại response cũ; nếu khác thông tin, báo lỗi `IDEMPOTENCY_KEY_REUSED`.

### 4.3. Sự khác biệt giữa các Cổng khi tạo thanh toán
- **VNPAY**:
  - Không cần gọi HTTP ra ngoài để lấy URL. URL được ký số tại chỗ (HMAC-SHA512).
  - Khởi tạo payment trạng thái `PENDING`, gán `checkout_url` và lưu DB trong 1 transaction duy nhất.
- **MoMo**:
  - Phải gọi HTTP POST sang MoMo API (`/v2/gateway/api/create`).
  - **Transaction 1**: Tạo payment trạng thái `CREATING`, `checkout_url = null`, hẹn lịch `next_reconcile_at = now() + 60s`.
  - Gọi MoMo qua `CircuitBreaker` (Resilience4j) với timeout cấu hình rõ ràng. Dùng chính `payment.id` làm MoMo `orderId` và `requestId` cố định để MoMo nhận diện retry idempotent.
  - **Nếu MoMo trả URL**: Transaction 2 cập nhật payment sang `PENDING` và lưu `checkout_url`.
  - **Nếu MoMo timeout / 5xx / Circuit breaker mở**: Giữ nguyên `CREATING`, không trả URL, hẹn scheduler reconcile bù; client nhận phản hồi `status = CREATING`.
  - **Nếu MoMo từ chối nghiệp vụ rõ ràng (bad request, invalid param)**: Cập nhật payment sang `FAILED`.

## 5. Cơ chế Idempotency khi nhận IPN/Webhook từ Cổng

### 5.1. Xác thực và Khóa bi quan (Pessimistic Locking)
1. Gateway Strategy kiểm tra chữ ký số (`signatureValid`). Nếu sai:
   - VNPAY trả JSON `{"RspCode": "97", "Message": "Invalid checksum"}`.
   - MoMo trả HTTP `400 Bad Request`.
2. Trích xuất `paymentId` từ `vnp_TxnRef` (VNPAY) hoặc `orderId` (MoMo). Nếu không tìm thấy:
   - VNPAY trả `{"RspCode": "01", "Message": "Order not found"}`.
   - MoMo trả HTTP `404 Not Found`.
3. Mở transaction và khóa dòng payment trong cơ sở dữ liệu:
   `paymentRepository.findByIdForUpdate(paymentId)`
4. So khớp số tiền (`amount`): Nếu số tiền IPN báo không khớp `payment.amount`:
   - VNPAY trả `{"RspCode": "04", "Message": "Invalid amount"}`.
   - MoMo trả HTTP `422 Unprocessable Entity`.

### 5.2. Chống xử lý trùng lặp và xung đột kết quả
1. **Kiểm tra `provider_transaction_id`**: Nếu payment đã có `provider_transaction_id` mà IPN gửi mã giao dịch khác, log error và trả lỗi xung đột:
   - VNPAY trả `{"RspCode": "99", "Message": "Conflicting transaction result"}`.
   - MoMo trả HTTP `409 Conflict`.
2. **Kiểm tra trạng thái hiện tại của Payment**:
   - Nếu payment **đã ở trạng thái kết thúc** (`SUCCEEDED`, `FAILED`, `CANCELLED`):
     - **Nếu trùng kết quả** (ví dụ đã `SUCCEEDED` và IPN lại báo thành công): Bỏ qua không xử lý lại, trả mã báo đã xử lý thành công trước đó:
       - VNPAY trả `{"RspCode": "02", "Message": "Order already confirmed"}`.
       - MoMo trả HTTP `204 No Content`.
       - Tuyệt đối không gọi `settlePaidOrder` hay phát hành vé lần thứ hai.
     - **Nếu mâu thuẫn kết quả** (ví dụ đã `SUCCEEDED` nhưng IPN lại báo FAILED): Không ghi đè DB, ghi log cảnh báo và trả mã xung đột (`CONFLICTING_RESULT`).
   - Nếu payment **đang chờ** (`PENDING` hoặc `CREATING`):
     - Lưu raw payload vào `webhook_payload`, cập nhật `webhook_received_at`, `webhook_signature_valid = true` và `provider_transaction_id`.
     - Nếu kết quả là `SUCCEEDED`:
       - Kiểm tra thời hạn giữ vé: `order.getStatus() == HELD` và `LocalDateTime.now().isBefore(order.getHoldExpiresAt())`.
       - **Còn hạn**: Chuyển `payment.status = SUCCEEDED`, `paid_at = now()`, gọi `orderInventoryService.settlePaidOrder` (order sang `CONFIRMED`) và phát hành vé `ticketIssuer.issueTickets`.
       - **Quá hạn**: Chuyển `payment.status = SUCCEEDED`, nhưng **không xác nhận order**, **không phát vé**; đánh dấu `refund_required = true` và ghi chú `failure_reason = "REFUND_REQUIRED: paid after hold expiry..."`.
     - Nếu kết quả là `FAILED` hoặc `CANCELLED`: Cập nhật `payment.status`, lưu `failure_reason`.
     - Xóa lịch `next_reconcile_at = null`.
     - Commit transaction thành công rồi mới phản hồi cho cổng (VNPAY `00`, MoMo `204`).

## 6. Đối soát bù chủ động (Reconciliation Scheduler Idempotency)

- Khi IPN bị mạng làm rơi hoặc gateway không gửi: `PaymentReconciliationScheduler` định kỳ quét các giao dịch đến hạn `next_reconcile_at`.
- Gọi `PaymentStatusQueryGateway` (VNPAY Query DR / MoMo Query API).
- Kết quả đối soát được đưa về cùng một hàm hội tụ `applyVerifiedOutcome(..., fromIpn = false)`, sử dụng chung cơ chế khóa bi quan, kiểm tra số tiền, và chống ghi đè idempotent y hệt như luồng IPN.