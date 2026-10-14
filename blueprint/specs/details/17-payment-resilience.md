# Đặc tả Kiến trúc: Thanh toán chống lỗi và Phục hồi sự cố (Payment Resilience & Fault Tolerance)

## 1. Mục tiêu và Nguyên tắc cốt lõi

Đặc tả này quy định kiến trúc chống chịu lỗi, xử lý bất đồng bộ và tự động phục hồi cho phân hệ thanh toán của TicketBox, giải quyết triệt để các rủi ro mạng, trễ pha và phân mảnh dữ liệu giữa TicketBox và các cổng thanh toán (VNPAY, MoMo).

### 4 Nguyên tắc cốt lõi:
1. **Tách biệt trạng thái Thanh toán và Đơn hàng:**
   - Trạng thái thanh toán (`PaymentStatus`: `INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `EXPIRED`) phản ánh dòng tiền thực tế với cổng.
   - Trạng thái đơn hàng (`OrderStatus`: `HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`) phản ánh quyền sở hữu vé của khán giả.
   - *Hệ quả:* "Tiền đã thu thành công" không đồng nghĩa "Vé đã phát hành". Khi tiền về muộn lúc vé đã hết hạn giữ chỗ, thanh toán vẫn được ghi nhận `SUCCESS` nhưng đơn hàng không phát vé và hệ thống tự động hoàn tiền.
2. **Khóa chống trùng 3 tầng (3-Tier Deduplication & Idempotency):**
   - *Tầng 1 (API Client)*: Redis Idempotency Store + `Idempotency-Key` (UUID v4) + Request Fingerprint (SHA-256).
   - *Tầng 2 (Sự kiện Cổng)*: Bảng `payment_events` với khóa duy nhất `UNIQUE (provider, dedupe_key)` chặn đứng mọi Webhook/IPN trùng lặp ngay từ tầng cơ sở dữ liệu.
   - *Tầng 3 (Giao dịch & Hoàn tiền)*: Bảng `payments` với `merchant_txn_ref` (UNIQUE) và bảng `refunds` với `idempotency_key` (UNIQUE).
3. **Điểm hội tụ xử lý duy nhất (Single Convergence Point):**
   - Mọi tín hiệu kết quả (IPN webhook, Query DR định kỳ, hoặc ReturnUrl query) đều được quy đổi thành cấu trúc chuẩn `PaymentOutcome` và chuyển qua một hàm xử lý duy nhất `processPaymentResult`.
   - Hàm này thực hiện khóa bi quan `SELECT ... FOR UPDATE` theo `merchant_txn_ref`, loại bỏ hoàn toàn tình trạng race-condition giữa nhiều nguồn dữ liệu.
4. **Kiểm soát đồng thời lạc quan (Optimistic Concurrency Control):**
   - Bảng `payments` chứa cột `version BIGINT` (`@Version`), ngăn chặn xung đột khi có sự phản hồi muộn từ cổng đè lên thao tác timeout/reconcile của hệ thống.

---

## 2. Mô hình Thực thể Dữ liệu Mới

~~~mermaid
erDiagram
    orders ||--o{ payments : "1 đơn có thể có nhiều lượt thanh toán"
    payments ||--o{ payment_events : "lưu vết mọi webhook / IPN / query"
    payments ||--o{ refunds : "lệnh hoàn tiền khi lỗi / trễ hạn"

    payments {
        uuid id PK
        uuid order_id FK
        varchar provider
        varchar merchant_txn_ref UK "Mã tham chiếu hệ thống tự sinh"
        varchar provider_txn_id "Mã cổng cấp, UNIQUE(provider, provider_txn_id)"
        numeric amount "Số tiền thanh toán"
        varchar currency "Mặc định VND"
        varchar status "INITIATED, PENDING, SUCCESS, FAILED, CANCELLED, EXPIRED"
        text payment_url "URL cổng cấp"
        varchar failure_code
        text failure_message
        bigint version "Optimistic locking"
        timestamptz paid_at
        timestamptz created_at
        timestamptz updated_at
    }

    payment_events {
        uuid id PK
        uuid payment_id FK "Nullable để lưu cả event không khớp payment"
        varchar provider
        varchar event_source "IPN, RETURN_URL, QUERY_API, INTERNAL"
        varchar dedupe_key UK "Khóa chống trùng cùng provider"
        varchar event_type "PAYMENT_SUCCESS, PAYMENT_FAILED, ..."
        jsonb raw_payload "Dữ liệu gốc phục vụ đối soát"
        boolean signature_valid "Ghi lại cả chữ ký sai để điều tra"
        varchar processing_status "RECEIVED, PROCESSED, FAILED, IGNORED"
        text error_message
        timestamptz received_at
        timestamptz processed_at
    }

    refunds {
        uuid id PK
        uuid payment_id FK
        numeric amount "Số tiền hoàn"
        varchar currency
        varchar idempotency_key UK "Chống tạo lệnh hoàn tiền trùng"
        varchar status "REQUESTED, PROCESSING, SUCCESS, FAILED"
        varchar provider_refund_id "Mã hoàn tiền do cổng cấp"
        text reason
        uuid requested_by "Actor yêu cầu (user hoặc system)"
        varchar failure_code
        text failure_message
        timestamptz requested_at
        timestamptz completed_at
    }
~~~

---

## 3. Xử lý 4 Ca lỗi Biên Trọng điểm (Fault Scenarios)

### Ca 1: Timeout khi gọi Gateway tạo URL thanh toán (MoMo)
- **Tình huống:** TicketBox gửi request khởi tạo giao dịch sang MoMo nhưng gặp HTTP 5xx, timeout hoặc mất mạng. Chưa rõ MoMo đã ghi nhận yêu cầu hay chưa.
- **Xử lý:**
  1. Lưu `payments` ở trạng thái `INITIATED`, lưu `failure_code = 'GATEWAY_TIMEOUT'`.
  2. Giữ nguyên đơn hàng `orders` ở trạng thái `HELD` (nếu còn trong thời hạn `hold_expires_at`).
  3. API trả về thông tin payment với trạng thái `INITIATED` (chưa có URL). Client hiển thị trạng thái chuẩn bị và bắt đầu polling.
  4. Worker chạy nền sẽ thực hiện gọi Query API sang MoMo bằng `merchant_txn_ref` (kèm backoff):
     - Nếu MoMo báo giao dịch **chưa tồn tại**: Cho phép chuyển payment sang `FAILED` và người dùng có thể thử lại với `merchant_txn_ref` mới.
     - Nếu MoMo báo giao dịch **đang chờ / đã có**: Lấy URL hoặc cập nhật kết quả thật vào payment.
     - Tuyệt đối không gửi request tạo mới mù quáng với cùng `merchant_txn_ref` nếu chưa query đối soát.

### Ca 2: Gateway trả URL sau khi hệ thống đã coi là Timeout (Late Response)
- **Tình huống:** Request tạo URL bị timeout ở phía TicketBox, nhưng vài giây sau MoMo mới trả kết quả về, hoặc worker đã can thiệp đánh dấu thất bại.
- **Xử lý:**
  1. Sử dụng cập nhật có điều kiện qua khóa lạc quan:
     ```sql
     UPDATE payments
     SET status = 'PENDING', payment_url = :url, version = version + 1
     WHERE id = :id AND status = 'INITIATED' AND version = :expectedVersion;
     ```
  2. Nếu cập nhật thành công (1 row): Lưu URL bình thường, người dùng có thể tiếp tục thanh toán.
  3. Nếu cập nhật thất bại (0 row - payment đã `FAILED` hoặc `EXPIRED`): Từ chối lưu URL, ghi sự kiện vào `payment_events` với `event_source = 'INTERNAL'` để lưu vết. Không mở URL cho người dùng.

### Ca 3: Không nhận được IPN trong thời gian giữ vé (Missing IPN)
- **Tình huống:** Người dùng đã thanh toán trên cổng nhưng mạng bị đứt, cổng không gửi được IPN hoặc TicketBox không nhận được.
- **Xử lý:**
  1. Worker định kỳ (mỗi 1 - 2 phút) quét các payment `PENDING` đã tồn tại quá ngưỡng cho phép (ví dụ > 2 phút kể từ khi tạo).
  2. Chủ động gọi Query API (Query DR của VNPAY, Query API của MoMo).
  3. Đưa kết quả xác minh vào hàm hội tụ `processPaymentResult`:
     - Nếu cổng báo **SUCCESS**: Xử lý hoàn tất đơn hàng và phát hành vé bình thường (nếu hold còn hạn). `dedupe_key` được tính bằng `provider_txn_id + "_SUCCESS"` để nếu sau đó IPN thật có về cũng sẽ bị chặn trùng lặp.
     - Nếu cổng báo **FAILED / CANCELLED**: Cập nhật payment tương ứng.
     - Nếu cổng báo **chưa thanh toán** và đơn hàng đã quá hạn `hold_expires_at`: Worker chuyển payment sang `EXPIRED`, nhả kho giữ vé.

### Ca 4: IPN thành công đến sau khi thời hạn giữ vé đã hết hạn (Late IPN)
- **Tình huống:** Cổng trừ tiền thành công của khách, nhưng IPN bị trễ nhiều phút và đến sau khi đơn hàng đã bị `OrderExpirationScheduler` nhả kho và chuyển sang `EXPIRED`. Ghế có thể đã bị người khác mua.
- **Chính sách chốt:** **TUYỆT ĐỐI KHÔNG TÁI CẤP VÉ — TỰ ĐỘNG HOÀN TIỀN 100%**.
- **Xử lý chi tiết:**
  1. Xác thực chữ ký số hợp lệ, lưu `payment_events`.
  2. Khóa dòng `payments` bằng `SELECT ... FOR UPDATE WHERE merchant_txn_ref = :ref`.
  3. Kiểm tra số tiền hợp lệ.
  4. Kiểm tra trạng thái đơn: `order.status != HELD` hoặc `now >= order.hold_expires_at`.
  5. Cập nhật `payments`:
     - `status = 'SUCCESS'` (ghi nhận sự thật tài chính: tiền đã về tài khoản).
     - `paid_at = now()`.
     - `failure_code = 'LATE_PAYMENT_REFUND_INITIATED'`.
     - `failure_message = 'Thanh toán thành công sau khi hết hạn giữ chỗ. Hệ thống tự động hoàn tiền.'`.
  6. Đơn hàng `orders` **giữ nguyên trạng thái `EXPIRED`**, **không phát hành vé**.
  7. Tạo bản ghi `refunds`:
     - `payment_id = payment.id`
     - `amount = payment.amount`
     - `idempotency_key = "LATE_REFUND_" + payment.merchant_txn_ref`
     - `status = 'REQUESTED'`
     - `reason = 'Late payment after hold expiry'`
     - `requested_by = SYSTEM_ACTOR_UUID`
  8. Gọi trực tiếp `RefundGateway.refund(...)` sang cổng:
     - **Nếu cổng xác nhận thành công**: Cập nhật `refunds.status = 'SUCCESS'`, `completed_at = now()`, lưu `provider_refund_id`.
     - **Nếu cổng phản hồi đang xử lý hoặc timeout**: Cập nhật `refunds.status = 'PROCESSING'` để worker retry đối soát tiếp theo hoàn tất.
     - **Nếu cổng từ chối**: Cập nhật `refunds.status = 'FAILED'`, bắn cảnh báo khẩn cho quản trị viên đối soát thủ công.
  9. Đánh dấu `payment_events.processing_status = 'PROCESSED'` và trả mã thành công (VNPAY `00`, MoMo `204`) cho cổng.

---

## 4. Điểm hội tụ xử lý kết quả (`processPaymentResult`)

Mọi luồng tiếp nhận kết quả (IPN webhook, kết quả truy vấn Query API, callback ReturnUrl) đều phải đi qua luồng chuẩn hóa sau:

~~~mermaid
flowchart TD
    A[Nhận tín hiệu: IPN / Query API / ReturnUrl] --> B[Ghi nhận vào payment_events: status = RECEIVED]
    B -->|Vi phạm UNIQUE provider + dedupe_key| C[Sự kiện trùng: ACK idempotency ngay, DỪNG]
    B -->|Thành công| D{Kiểm tra chữ ký số?}
    D -->|Sai| E[Cập nhật signature_valid = FALSE, status = IGNORED; Báo lỗi cổng]
    D -->|Đúng| F[Khóa bi quan payment: SELECT ... FOR UPDATE]
    F --> G{Kiểm tra số tiền amount?}
    G -->|Không khớp| H[payment_events: FAILED; Báo lỗi lệch tiền]
    G -->|Khớp| I{Trạng thái hiện tại của payment?}
    I -->|Đã SUCCESS / Terminal| J[Đã xử lý trước đó: ACK idempotency trùng]
    I -->|INITIATED hoặc PENDING| K{Kết quả giao dịch?}
    K -->|FAILED / CANCELLED| L[Cập nhật payment FAILED/CANCELLED; Order giữ HELD nếu còn hạn]
    K -->|SUCCESS| M{Hold của order còn hạn?}
    M -->|Còn hạn| N[payment: SUCCESS; order: CONFIRMED; Settle kho & Phát hành vé]
    M -->|Hết hạn| O[payment: SUCCESS; order: EXPIRED; Tạo refunds & Gọi Gateway Refund API]
    L --> P[payment_events: PROCESSED]
    N --> P
    O --> P
    P --> Q[Commit Database Transaction]
    Q --> R[Trả phản hồi ACK cho cổng: VNPAY 00, MoMo 204]
~~~

---

## 5. Kiến trúc Triển khai Worker theo Profile

Để phục vụ môi trường monolith hiện tại đồng thời sẵn sàng mở rộng sang kiến trúc container độc lập trong production, các tác vụ nền được phân tách theo Spring Profile:

- **Profile `worker`**:
  - Chịu trách nhiệm quét `OrderExpirationScheduler` (nhả kho khi hết hạn).
  - Quét `PaymentReconciliationScheduler` (đối soát bù các giao dịch treo).
  - Quét `RefundProcessingScheduler` (xử lý lại các lệnh hoàn tiền đang `REQUESTED` hoặc `PROCESSING`).
- **Cấu hình Spring:**
  ```java
  @Configuration
  @Profile({"worker", "default"}) // Mặc định chạy trong app ở môi trường dev; tách biệt ở production
  public class PaymentWorkerConfig {
      // Scheduler beans initialization
  }
  ```
- **Chạy ứng dụng:**
  - Ở local/dev: Chạy mặc định `./mvnw spring-boot:run` (bao gồm cả web và worker).
  - Ở production: Tách làm 2 service:
    1. Web API Pods: `--spring.profiles.active=prod,no-worker` (chỉ xử lý REST API và Webhook tốc độ cao, không chạy scheduled jobs).
    2. Dedicated Worker Pod: `--spring.profiles.active=prod,worker` (chỉ chạy background schedulers, không mở public traffic).

---

## 6. Ma trận Cổng thanh toán & Tích hợp Refund API

| Cổng | Tạo URL | Nhận IPN Webhook | Query Trạng thái | Hoàn tiền tự động (Refund) |
|---|---|---|---|---|
| **VNPAY** | Ký HMAC-SHA512 tại chỗ (Local) | `GET /payments/vnpay/ipn` (RspCode `00`, `02`, `97`, `04`, `99`) | VNPAY Query DR API (`vnp_Command=querydr`) | VNPAY Refund API (`vnp_Command=refund`, TransactionType `02` hoặc `03`) |
| **MoMo** | HTTP POST `/v2/gateway/api/create` (Bảo vệ bằng Circuit Breaker) | `POST /payments/momo/ipn` (HTTP 204, 400, 404, 422, 409) | MoMo Query API (`/v2/gateway/api/query`) | MoMo Refund API (`/v2/gateway/api/refund`) |
