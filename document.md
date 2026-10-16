# Danh sách bảng
1. payments

id: khóa chính.
order_id: khóa ngoại tới orders, NOT NULL. Một đơn có thể có nhiều lần thanh toán nếu lần đầu thất bại.
provider: VNPAY, MOMO, ZALOPAY... NOT NULL.
merchant_txn_ref: mã tham chiếu do hệ thống của bạn tự sinh trước khi gọi cổng (ví dụ vnp_TxnRef, orderId của MoMo). NOT NULL, UNIQUE. Đây là chìa khóa để ghép IPN với đúng payment, và cũng là nền tảng cho idempotency khi gọi lại cổng.
provider_txn_id: mã giao dịch do cổng cấp. Nullable vì lúc mới tạo chưa có. UNIQUE trên (provider, provider_txn_id).
amount: BIGINT (VND không có phần thập phân). NOT NULL, CHECK amount > 0.
currency: mặc định 'VND'.
status: INITIATED, PENDING, SUCCESS, FAILED, CANCELLED, EXPIRED. NOT NULL.
payment_url: URL đã trả về từ cổng. Nullable, lưu lại để có thể mở lại khi người dùng quay lại trang thanh toán.
failure_code và failure_message: nullable, dùng cho debug và hiển thị lý do thất bại.
created_at, updated_at, paid_at (nullable).
version: số phiên bản cho optimistic locking, tránh hai luồng (IPN và trang return) cùng cập nhật một payment.

2. payment_events

id: khóa chính.
payment_id: nullable, khóa ngoại tới payments. Có thể để null nếu IPN đến mà không tìm thấy payment khớp, để vẫn lưu lại để điều tra.
provider: NOT NULL.
event_source: IPN, RETURN_URL, QUERY_API, hoặc INTERNAL. NOT NULL.
dedupe_key: khóa chống trùng lặp. NOT NULL, UNIQUE trên (provider, dedupe_key). Nếu cổng có mã sự kiện riêng thì dùng mã đó. Nếu không, tạo hash từ các trường quan trọng của payload.
event_type: loại sự kiện, ví dụ PAYMENT_SUCCESS hay PAYMENT_FAILED.
raw_payload: nguyên văn dữ liệu nhận được (JSON). NOT NULL. Đây là bằng chứng khi đối soát hoặc tranh chấp.
signature_valid: BOOLEAN NOT NULL. Ghi lại cả những event có chữ ký sai, để phục vụ điều tra, nhưng không xử lý chúng.
processing_status: RECEIVED, PROCESSED, FAILED, IGNORED. NOT NULL.
error_message: nullable.
received_at NOT NULL, processed_at nullable.

3. refunds

id: khóa chính.
payment_id: khóa ngoại tới payments, NOT NULL.
amount: BIGINT, NOT NULL, CHECK amount > 0. Có thể hoàn một phần, nên một payment có thể có nhiều refund. Tổng các refund thành công không được vượt quá số tiền đã thanh toán. Ràng buộc này cần kiểm tra trong transaction ở tầng ứng dụng, vì SQL thường không biểu diễn trực tiếp được.
idempotency_key: NOT NULL, UNIQUE. Dùng để không tạo hai lệnh hoàn tiền cho cùng một yêu cầu khi người dùng bấm nhiều lần.
status: REQUESTED, PROCESSING, SUCCESS, FAILED. NOT NULL.
provider_refund_id: nullable, do cổng cấp sau khi nhận lệnh.
reason: nullable, nhưng nên có để đối soát.
requested_by: ID người dùng hoặc admin đã yêu cầu. NOT NULL.
failure_code: nullable.
requested_at NOT NULL, completed_at nullable.

# Xử lý URL thanh toán

Luồng thành công

Bước 1: Tạo đơn và giữ ghế

Tạo orders với trạng thái PENDING.
Tạo seat_holds kèm expires_at (ví dụ 10 phút).
Tạo payments với trạng thái INITIATED, sinh merchant_txn_ref duy nhất.

Bước 2: Gọi gateway để lấy URL thanh toán

Gửi yêu cầu tạo giao dịch kèm merchant_txn_ref, amount và return_url.
Nhận payment_url, lưu vào payments và chuyển trạng thái sang PENDING.
Chuyển hướng người dùng sang payment_url.

Bước 3: Người dùng thanh toán trên trang của cổng

Hệ thống không nhận thông tin thẻ, chỉ nhận kết quả từ cổng.
Cổng xử lý giao dịch và gửi hai tín hiệu: IPN (server gọi server) và redirect trình duyệt về return_url.

Bước 4: Xử lý IPN (đây là nguồn sự thật)
Trong ví dụ dưới, payment_events có ràng buộc UNIQUE trên (provider, dedupe_key):

Xác thực chữ ký. Nếu sai, ghi payment_events với signature_valid = 0 rồi trả lỗi cho cổng, không xử lý tiếp.
Tính dedupe_key và chèn một dòng vào payment_events với processing_status = RECEIVED. Nếu vi phạm UNIQUE, đây là sự kiện trùng, trả về “đã nhận” cho cổng và dừng.
Trong một transaction, khóa dòng payment:
sql
SELECT id, status, amount, order_id, version
FROM payments
WHERE merchant_txn_ref = :ref
FOR UPDATE;
Kiểm tra amount từ IPN có khớp payments.amount không. Nếu không khớp, không đánh dấu thành công, chuyển sang trạng thái cần đối soát.
Nếu payment còn PENDING hoặc INITIATED và đơn còn PENDING, cập nhật trong cùng transaction:
payments: status = SUCCESS, provider_txn_id, paid_at.
orders: status = PAID.
seat_holds: chuyển ghế thành đã bán (tạo order_items và tickets, xóa hold).
payment_events: processing_status = PROCESSED.
Commit, rồi trả về mã thành công đúng định dạng của cổng (ví dụ VNPay yêu cầu RspCode = 00).

Bước 5: Xử lý return URL

Trang return chỉ dùng để hiển thị kết quả cho người dùng. Không dựa vào tham số trên URL để đánh dấu thành công, vì người dùng có thể sửa được.
Trang này đọc trạng thái từ DB. Nếu IPN chưa về, hiển thị “Đang xử lý” và kiểm tra lại sau vài giây. Có thể gọi QUERY API của cổng để chủ động hỏi kết quả.
Luồng thất bại

Có hai trường hợp chính, và hệ thống xử lý gần giống nhau. Điểm quan trọng: thất bại của payment không có nghĩa là đơn hàng thất bại. Người dùng vẫn có thể thử lại bằng phương thức khác, miễn là ghế còn được giữ.

Trường hợp A: Người dùng hủy trên trang cổng

Cổng redirect về return_url với mã hủy (ví dụ VNPay trả mã 24).
Hệ thống ghi payment_events với event_source = RETURN_URL.
payments: status = CANCELLED, lưu failure_code.
orders giữ PENDING, seat_holds vẫn còn hiệu lực. Người dùng có thể tạo payment mới cho cùng đơn.

Trường hợp B: Không đủ số dư hoặc giao dịch bị từ chối

Cổng gửi IPN với mã lỗi (ví dụ VNPay trả mã 51).
Quy trình IPN giống luồng thành công, nhưng nhánh cuối là:
payments: status = FAILED, lưu failure_code và failure_message.
payment_events: processing_status = PROCESSED.
orders vẫn PENDING, seat_holds không đổi.
Trang return hiển thị lý do và nút thử lại.

Quy tắc khi thử lại

Mỗi lần thử là một dòng mới trong payments, với merchant_txn_ref mới. Không tái sử dụng merchant_txn_ref cũ.
Trước khi tạo payment mới, kiểm tra đơn còn PENDING và hold còn hạn. Nếu không, yêu cầu người dùng chọn lại ghế.
Nếu đã có một payment SUCCESS cho đơn, không được tạo payment mới. Ràng buộc này nên được kiểm tra trong transaction.

# Xử lý IPN thanh toán

1. Timeout khi gọi gateway để tạo URL

Khi request timeout, bạn không biết cổng đã tạo giao dịch hay chưa. Vì vậy không nên đánh dấu FAILED ngay hoặc tạo payment mới ngay.

Giữ payments ở trạng thái INITIATED, ghi failure_code = 'GATEWAY_TIMEOUT'.
Giữ seat_holds để người dùng còn thời gian thử lại.
Gọi QUERY API của cổng bằng merchant_txn_ref, sau một khoảng chờ ngắn (có backoff).
Nếu cổng không có giao dịch đó: chuyển payment sang FAILED (hoặc EXPIRED), rồi cho phép tạo payment mới với merchant_txn_ref mới.
Nếu cổng có giao dịch: lấy payment_url hoặc trạng thái thật từ cổng và cập nhật tiếp.
Giới hạn số lần retry. Sau ngưỡng, báo lỗi cho người dùng và để job dọn dẹp xử lý.

Không nên gửi lại request tạo giao dịch với cùng merchant_txn_ref một cách mù quáng. Một số cổng sẽ từ chối vì trùng mã, một số khác có thể tạo hai giao dịch.

2. Gateway trả URL sau khi hệ thống đã coi là timeout

Đây là trường hợp “response đến muộn”: request phía bạn đã đóng hoặc job timeout đã chạy, nhưng response vẫn về. Hai luồng có thể cùng cập nhật một payment, nên cần kiểm soát đồng thời.

Khi nhận response muộn, kiểm tra trạng thái hiện tại của payment trước khi ghi:

sql
UPDATE payments
SET status = 'PENDING', payment_url = :url, version = version + 1
WHERE id = :id AND status = 'INITIATED' AND version = :v;
Nếu cập nhật được 1 dòng: lưu URL, người dùng có thể tiếp tục thanh toán.
Nếu cập nhật 0 dòng: payment đã FAILED hoặc EXPIRED. Không trả URL này cho người dùng. Ghi payment_events với event_source = INTERNAL để đối soát. Nếu cổng có API hủy giao dịch thì gọi để hủy, nếu không thì để URL tự hết hạn.

Lưu ý: giao dịch đã được tạo ở cổng vẫn có thể được thanh toán sau đó, nên IPN có thể về. Điều này sẽ được xử lý ở mục 4.

3. Không nhận được IPN trong thời gian hold

Im lặng từ IPN không có nghĩa là thất bại. Có ba khả năng: người dùng chưa thanh toán, người dùng đã thanh toán nhưng IPN bị mất, hoặc người dùng bỏ đi giữa chừng.

Cách xử lý là một job đối soát định kỳ (ví dụ mỗi 1 đến 2 phút):

Chọn các payment ở trạng thái PENDING đã quá một ngưỡng (ví dụ 2 phút sau khi tạo) và gọi QUERY API cho từng payment.
Kết quả là SUCCESS: đi qua cùng hàm xử lý kết quả như IPN. dedupe_key có thể lấy từ provider_txn_id cộng với trạng thái, để tránh xử lý hai lần nếu IPN về sau.
Kết quả là FAILED hoặc CANCELLED: cập nhật payment tương ứng.
Chưa có kết quả: giữ nguyên và kiểm tra lại sau.
Hold hết hạn mà payment vẫn PENDING: chuyển payment sang EXPIRED và nhả ghế. Không xóa payment, vì bản ghi này cần cho đối soát.

Việc nhả ghế nên dùng điều kiện cập nhật rõ ràng (chỉ nhả khi hold còn thuộc đơn đó và đơn còn PENDING), để không vô tình nhả ghế của người khác.

4. IPN đến sau khi hold đã hết hạn

Đây là trường hợp khó nhất, vì tiền có thể đã bị trừ trong khi ghế đã được nhả và có thể đã bán cho người khác.

Sau khi verify chữ ký, lưu payment_events và khóa payment bằng FOR UPDATE, hệ thống kiểm tra hai điều:

Ghế còn trống và đơn chưa bị hủy: có thể tạo lại hold và xử lý như thành công. Đây là chính sách kinh doanh, bạn cần quyết định có cho phép hay không.

Ghế đã có người mua hoặc đơn đã hủy: không phát vé. Cụ thể:

Đánh dấu payments là SUCCESS (vì tiền đã thu thật), nhưng đơn không chuyển sang PAID.
Tạo bản ghi trong refunds với idempotency_key được suy ra từ merchant_txn_ref, ví dụ thêm hậu tố LATE_REFUND, để không tạo hai lệnh hoàn tiền nếu IPN lặp lại.
Gọi API hoàn tiền của cổng, cập nhật refunds theo kết quả, và thông báo cho người dùng.

Trường hợp này cho thấy một điểm thiết kế quan trọng: trạng thái thanh toán và trạng thái đơn hàng nên tách biệt. “Tiền đã thu” không đồng nghĩa “vé đã phát”. Nếu gộp chung, bạn sẽ khó xử lý đúng các ca như trên.

Nguyên tắc chung

Tất cả các nhánh trên nên hội tụ về một hàm xử lý kết quả thanh toán duy nhất. Hàm này nhận đầu vào là kết quả đã xác minh (từ IPN, QUERY hoặc return URL), khóa payment, kiểm tra trạng thái hiện tại, rồi quyết định hành động. Khi mọi nguồn đi qua một điểm, bạn chỉ cần kiểm thử và bảo đảm idempotency ở một nơi.

# Triển khai Worker
- Triển khai bằng Profile

---

- Session 001
  - Goal: Khởi động task, chạy baseline verification, inspect codebase hiện tại, clean và khởi tạo `PROGRESS.md`, thực hiện interview làm rõ yêu cầu, chuẩn hóa docs baseline, thiết kế Specs & DB Design mới, ghi nhận DECISIONS.md và lập Implementation & Test Plan.
  - Completed:
    - Xác nhận thư mục làm việc `pwd` và lịch sử commit gần nhất `git log --oneline -5`.
    - Chạy `./init.sh` kiểm tra baseline: Backend tests đạt **212/212 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất không lỗi.
    - Inspect toàn bộ codebase `backend/src/main/java/com/ticketbox/api/module/payment`, `order`, `idempotency`, đọc chi tiết `document.md`, `17-payment-resilience.md`, `10-order-checkout.md`, `03-payment-idempotency.md`, `payments.md`.
    - Thực hiện phỏng vấn làm rõ yêu cầu theo skill `interview-me`:
      - Chốt chính sách: IPN thành công đến sau khi hold hết hạn -> **Luôn hoàn tiền (Refund), tuyệt đối không cấp vé**.
      - Chốt phạm vi tích hợp: **Tích hợp API Refund tự động của cả MoMo và VNPay** ngay trong đợt refactor này.
    - **Deliverable 1 (Chuẩn hóa Docs Baseline)**:
      - Cập nhật [`blueprint/specs/details/03-payment-idempotency.md`](blueprint/specs/details/03-payment-idempotency.md) khớp chính xác với code `IdempotencyService`, fingerprint, pessimist locking, reconciliation query hiện tại.
      - Cập nhật [`blueprint/specs/details/10-order-checkout.md`](blueprint/specs/details/10-order-checkout.md) khớp chính xác với luồng giữ vé, Circuit Breaker MoMo, tạo URL VNPAY tại chỗ, IPN vs ReturnUrl, và retry payment.
    - **Deliverable 2 (Đặc tả Kiến trúc Chống lỗi Mới)**:
      - Viết lại toàn diện [`blueprint/specs/details/17-payment-resilience.md`](blueprint/specs/details/17-payment-resilience.md): 4 nguyên tắc cốt lõi, mô hình 3 bảng, sơ đồ ERD, xử lý chi tiết 4 ca lỗi biên, lưu đồ hội tụ `processPaymentResult`, kiến trúc Worker theo profile, ma trận API Gateway.
    - **Deliverable 3 (Database Design)**:
      - Cập nhật [`blueprint/database-design/details/payments.md`](blueprint/database-design/details/payments.md) bổ sung `merchant_txn_ref` (UNIQUE), `version` (`@Version`), `failure_code`, `failure_message`.
      - Tạo mới [`blueprint/database-design/details/payment_events.md`](blueprint/database-design/details/payment_events.md) đặc tả nhật ký sự kiện bất biến và chống trùng `(provider, dedupe_key)`.
      - Tạo mới [`blueprint/database-design/details/refunds.md`](blueprint/database-design/details/refunds.md) đặc tả quản lý lệnh hoàn tiền và idempotency.
      - Cập nhật mục lục [`blueprint/database-design/database-design.md`](blueprint/database-design/database-design.md).
    - **Deliverable 4 (Architectural Decision)**:
      - Tạo [`04-payment-resilience-events-and-refunds.md`](.agents/DECISIONS/04-payment-resilience-events-and-refunds.md) và lập chỉ mục trong [`DECISIONS.md`](DECISIONS.md).
    - **Deliverable 5 (Implementation & Test Plan)**:
      - Xây dựng kế hoạch triển khai và kiểm thử 5 Phase chi tiết.
  - Commits: `5285598` - docs: standardize baseline payment specs and design fault-tolerant resilience architecture.
  - Files or artifacts updated:
    - `PROGRESS.md`, `DECISIONS.md`, `.agents/DECISIONS/04-payment-resilience-events-and-refunds.md`, `03-payment-idempotency.md`, `10-order-checkout.md`, `17-payment-resilience.md`, `payments.md`, `payment_events.md`, `refunds.md`, `database-design.md`.
  - Known risk or unresolved issue: Không có.
  - Next best step: Bắt đầu triển khai Phase 1.

- Session 002
  - Goal: Triển khai hoàn thiện Phase 1 (Flyway Migration V4, JPA Entities, Repositories, DTOs & Mappers, Unit/Integration Tests).
  - Completed:
    - **Migration**: Tạo [`V4__payment_resilience_events_refunds.sql`](backend/src/main/resources/db/migration/V4__payment_resilience_events_refunds.sql) bổ sung `merchant_txn_ref` (UNIQUE), `version` (optimistic lock), `failure_code` cho `payments`; tạo bảng `payment_events` và `refunds`.
    - **Enums & Entities**:
      - Mở rộng [`PaymentStatus.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentStatus.java) hỗ trợ `INITIATED` và `SUCCESS`.
      - Tạo [`PaymentEventSource.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEventSource.java) và [`PaymentEventStatus.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEventStatus.java).
      - Tạo [`RefundStatus.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/RefundStatus.java).
      - Cập nhật [`Payment.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/Payment.java) với `merchantTxnRef`, `@Version Long version`, `failureCode`, quan hệ `paymentEvents` và `refunds`.
      - Tạo entity [`PaymentEvent.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEvent.java) và [`Refund.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/Refund.java).
    - **Repositories**:
      - Cập nhật [`PaymentRepository.java`](backend/src/main/java/com/ticketbox/api/module/payment/repositories/PaymentRepository.java) bổ sung `findByMerchantTxnRef` và `findByMerchantTxnRefForUpdate`.
      - Tạo [`PaymentEventRepository.java`](backend/src/main/java/com/ticketbox/api/module/payment/repositories/PaymentEventRepository.java).
      - Tạo [`RefundRepository.java`](backend/src/main/java/com/ticketbox/api/module/payment/repositories/RefundRepository.java).
    - **DTOs & Mappers**:
      - Tạo [`RefundResponse.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundResponse.java) và [`PaymentEventResponse.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentEventResponse.java).
      - Cập nhật [`PaymentResponse.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentResponse.java) bổ sung `merchantTxnRef`, `failureCode`, `version`.
      - Tạo MapStruct mapper [`RefundMapper.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/mappers/RefundMapper.java) và [`PaymentEventMapper.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/mappers/PaymentEventMapper.java).
    - **Testing & Verification**:
      - Viết unit test [`RefundMapperTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/domain/mappers/RefundMapperTest.java).
      - Viết unit test [`PaymentEventMapperTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/domain/mappers/PaymentEventMapperTest.java).
      - Viết unit test [`PaymentEntityTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/domain/entities/PaymentEntityTest.java).
      - Viết integration test [`PaymentPersistenceIntegrationTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/PaymentPersistenceIntegrationTest.java).
      - Toàn bộ test suite backend đạt **222/222 passed** (0 failures, 0 errors, 2 skipped).
      - Script `./init.sh` chạy thành công 100%.
  - Commits: Sẵn sàng commit Phase 1.
  - Files or artifacts updated:
    - `backend/src/main/resources/db/migration/V4__payment_resilience_events_refunds.sql`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentStatus.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEventSource.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEventStatus.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/RefundStatus.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/Payment.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/PaymentEvent.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/entities/Refund.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/repositories/PaymentRepository.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/repositories/PaymentEventRepository.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/repositories/RefundRepository.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundResponse.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentEventResponse.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentResponse.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/mappers/RefundMapper.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/mappers/PaymentEventMapper.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/domain/mappers/RefundMapperTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/domain/mappers/PaymentEventMapperTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/domain/entities/PaymentEntityTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/PaymentPersistenceIntegrationTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Tiến hành Phase 2 (Gateway Refund Clients cho MoMo và VNPay).

- Session 003
  - Goal: Triển khai hoàn thiện Phase 2: Gateway Refund Clients (`MomoRefundGateway`, `VnpayRefundGateway`), DTOs, cấu hình và unit tests.
  - Completed:
    - **DTOs & Interfaces**:
      - Tạo [`RefundGatewayRequest.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundGatewayRequest.java) (refundId, merchantTxnRef, providerTxnId, amount, reason, transDate).
      - Tạo [`RefundGatewayResult.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundGatewayResult.java) (providerRefundId, status, responseCode, message).
      - Tạo interface [`PaymentRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/PaymentRefundGateway.java).
    - **Configuration**:
      - Bổ sung cấu hình `refund-url` cho MoMo và VNPay trong [`application.yml`](backend/src/main/resources/application.yml) và [`application.yml`](backend/src/test/resources/application.yml).
    - **Gateway Implementations**:
      - Triển khai [`MomoRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/MomoRefundGateway.java): Ký HMAC-SHA256 chuẩn MoMo API v2, bọc qua CircuitBreaker ("momo-refund"), xử lý timeout/lỗi mạng trả về `PROCESSING` kèm mã `GATEWAY_TIMEOUT` để scheduler tiếp tục đối soát, xử lý mở mạch `CIRCUIT_BREAKER_OPEN`.
      - Triển khai [`VnpayRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java): Ký HMAC-SHA512 chuẩn VNPay Web API 2.1.0 cho `vnp_Command=refund` (loại 02 hoàn toàn phần), xác thực checksum phản hồi `vnp_SecureHash`, bọc qua CircuitBreaker ("vnpay-refund"), xử lý timeout trả về `PROCESSING` và mở mạch `CIRCUIT_BREAKER_OPEN`.
    - **Unit Tests**:
      - Viết [`MomoRefundGatewayTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/gateways/MomoRefundGatewayTest.java) (8 tests): bao phủ ký request, kiểm tra format, mock server response thành công, parameterized mapping mã kết quả (0, 1000, 1004, 99), xử lý timeout và circuit breaker open, kiểm tra validation đầu vào.
      - Viết [`VnpayRefundGatewayTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java) (12 tests): bao phủ ký request, kiểm tra checksum phản hồi, từ chối chữ ký sai, parameterized mapping response code/transaction status (00/00, 91, 94, 00/01, 00/02, 99, 02), xử lý timeout và circuit breaker open, kiểm tra validation đầu vào.
    - **Verification**:
      - Toàn bộ backend test suite đạt **242/242 passed** (0 failures, 0 errors, 2 skipped).
      - Chạy `./init.sh` thành công 100% (cả backend tests và frontend build).
  - Commits: Sẵn sàng commit Phase 2.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundGatewayRequest.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundGatewayResult.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/PaymentRefundGateway.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/MomoRefundGateway.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java`
    - `backend/src/main/resources/application.yml`
    - `backend/src/test/resources/application.yml`
    - `backend/src/test/java/com/ticketbox/api/module/payment/gateways/MomoRefundGatewayTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Tiến hành Phase 3 (Refactor `PaymentServiceImpl` với hàm hội tụ `processPaymentResult`, triển khai `RefundService`, tích hợp Audit Log đầy đủ).

- Session 004
  - Goal: Hoàn thành triển khai Phase 3: Hàm hội tụ `processPaymentResult`, khóa bi quan, xử lý Late IPN và hoàn tiền tự động, dịch vụ hoàn tiền `RefundService` / `RefundServiceImpl`, và Audit Logging đầy đủ.
  - Completed:
    - **Convergence Point & Resilience**:
      - Tạo [`PaymentOutcome.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentOutcome.java) thống nhất dữ liệu từ IPN, Query Gateway, và Return URL.
      - Cập nhật [`PaymentService.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentService.java) và [`PaymentServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java) triển khai hàm hội tụ duy nhất `processPaymentResult(PaymentOutcome)`.
      - Triển khai cơ chế khóa bi quan (`findByMerchantTxnRefForUpdate` / fallback theo UUID `findByIdForUpdate`).
      - Triển khai ghi sự kiện bất biến `payment_events (provider, dedupe_key)` nhằm chống trùng lặp IPN tuyệt đối.
      - Xử lý Late IPN: Khi hold expired / order expired, đánh dấu payment `SUCCESS`, giữ nguyên đơn hàng `EXPIRED` (tuyệt đối không xuất vé), tự động tạo lệnh refund và kích hoạt qua `TransactionSynchronizationManager.registerSynchronization` sau khi commit giao dịch payment.
    - **Refund Management (`RefundService` & `RefundServiceImpl`)**:
      - Tạo interface [`RefundService.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/RefundService.java).
      - Triển khai [`RefundServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java) tuân thủ tách biệt 2 giao dịch theo Quyết định 04 trong `DECISIONS.md`:
        - Tx1: Atomic claim trạng thái `REQUESTED -> PROCESSING` thông qua `RefundRepository.updateStatusIfExpected(...)`.
        - External Gateway Call: Thực thi hoàn tiền với `MomoRefundGateway` / `VnpayRefundGateway` bên ngoài transaction/khóa payment.
        - Tx2: Cập nhật kết quả cuối cùng (`SUCCESS`, `FAILED`, hoặc `PROCESSING` khi timeout) và ghi nhận audit log tương ứng.
    - **Audit Logging**:
      - Tích hợp chuẩn hóa `AuditLogService` ghi log nhóm 1 (`REQUIRED` trong nghiệp vụ chính) và nhóm 2 (`REQUIRES_NEW` cho các sự kiện độc lập) tuân thủ `16-audit-logging.md`.
      - Ghi log chi tiết cho các hành động: `PAYMENT_CONVERGENCE_SUCCESS`, `PAYMENT_CONVERGENCE_FAILED`, `PAYMENT_CONVERGENCE_LATE_REFUND`, `REQUEST_REFUND`, `COMPLETE_REFUND`, `FAIL_REFUND`.
    - **Verification & Testing**:
      - Viết unit test suite [`RefundServiceImplTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java) (5/5 tests passed).
      - Viết integration test suite [`PaymentResilienceIntegrationTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java) (6/6 tests passed).
      - Cập nhật [`PaymentResilienceServiceTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java) tương thích mocks mới (13/13 tests passed).
      - Toàn bộ backend test suite đạt **253/253 passed** (0 failures, 0 errors, 2 skipped).
      - Chạy `./init.sh` toàn diện: Backend tests pass 100%, Frontend compile & build pass 100%.
  - Commits: `d3f1758` - feat(payment): implement Phase 3 - convergence handler, refund service, and audit logging.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentOutcome.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentService.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/RefundService.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/repositories/RefundRepository.java`
    - `backend/src/main/java/com/ticketbox/api/module/audit/domain/entities/AuditLog.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentReconciliationScheduler.java`
    - `backend/src/test/resources/application.yml`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Tiến hành Phase 4 (Worker profile và Schedulers định kỳ đối soát và xử lý refund backlog).

- Session 005
  - Goal: Hoàn thiện Phase 4 (Schedulers định kỳ kết hợp RabbitMQ Producer-Consumer theo kiến trúc Modular Monolith §5 trong `17-payment-resilience.md`) và Phase 5 (Unit test & `./init.sh` toàn diện).
  - Completed:
    - **Supporting Bug Fix**:
      - Sửa [`PaymentCompletionPublisher.java`](backend/src/main/java/com/ticketbox/api/module/payment/producers/PaymentCompletionPublisher.java) kiểm tra trạng thái thành công cho cả `PaymentStatus.SUCCESS` và `PaymentStatus.SUCCEEDED`, khắc phục việc định tuyến nhầm các giao dịch thành công sang `notification.payment-failed.queue`. Sử dụng constructor tường minh thay cho Lombok.
    - **RabbitMQ Infrastructure**:
      - Bổ sung các hằng số hàng đợi và routing key vào [`RabbitMqConstants.java`](backend/src/main/java/com/ticketbox/api/infrastructure/config/RabbitMqConstants.java): `q.payment.reconcile-check`, `payment.reconcile`, `q.payment.refund-process`, `payment.refund-process`.
      - Khai báo các Bean Queue và Binding tương ứng trong [`PaymentRabbitConfig.java`](backend/src/main/java/com/ticketbox/api/module/payment/config/PaymentRabbitConfig.java).
    - **DTOs & Repository**:
      - Tạo mới [`PaymentReconcileMessage.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentReconcileMessage.java) và [`RefundProcessMessage.java`](backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundProcessMessage.java).
      - Bổ sung truy vấn phân trang `findPendingRefundIds(List<RefundStatus>, Pageable)` vào [`RefundRepository.java`](backend/src/main/java/com/ticketbox/api/module/payment/repositories/RefundRepository.java).
      - Bổ sung `void reconcilePayment(UUID paymentId)` vào interface [`PaymentService.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentService.java).
    - **Schedulers (Worker Profile)**:
      - Nâng cấp [`PaymentReconciliationScheduler.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentReconciliationScheduler.java): Quét các payment quá hạn ở trạng thái `INITIATED`, `CREATING`, `PENDING`, đóng gói `PaymentReconcileMessage` và publish vào RabbitMQ; cấu hình `@Profile({"api", "worker"})`.
      - Tạo mới [`RefundProcessingScheduler.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/RefundProcessingScheduler.java): Quét các lệnh hoàn tiền tồn đọng ở trạng thái `REQUESTED`, `PROCESSING`, đóng gói `RefundProcessMessage` và publish vào RabbitMQ; cấu hình `@Profile({"api", "worker"})`.
    - **Consumers**:
      - Tạo mới [`PaymentReconciliationConsumer.java`](backend/src/main/java/com/ticketbox/api/module/payment/consumers/PaymentReconciliationConsumer.java): Lắng nghe `q.payment.reconcile-check` và gọi `paymentService.reconcilePayment(paymentId)`.
      - Tạo mới [`RefundProcessingConsumer.java`](backend/src/main/java/com/ticketbox/api/module/payment/consumers/RefundProcessingConsumer.java): Lắng nghe `q.payment.refund-process` và gọi `refundService.processRefund(refundId)`.
    - **Unit Tests & Verification**:
      - Viết [`PaymentCompletionPublisherTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/producers/PaymentCompletionPublisherTest.java) (7 tests passed).
      - Viết [`PaymentReconciliationSchedulerTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentReconciliationSchedulerTest.java) (2 tests passed).
      - Viết [`RefundProcessingSchedulerTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/RefundProcessingSchedulerTest.java) (2 tests passed).
      - Viết [`PaymentReconciliationConsumerTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/consumers/PaymentReconciliationConsumerTest.java) (3 tests passed).
      - Viết [`RefundProcessingConsumerTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/consumers/RefundProcessingConsumerTest.java) (3 tests passed).
      - Cập nhật [`PaymentControllerTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/controllers/PaymentControllerTest.java).
      - Toàn bộ backend test suite đạt **270/270 passed** (0 failures, 0 errors, 2 skipped).
      - Chạy `./init.sh` toàn diện: Backend tests pass 100%, Frontend Vite build pass 100%.
  - Commits: `118735b` - feat(payment): implement Phase 4 and 5 - schedulers, rabbitmq worker queue dispatch, and verification.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/infrastructure/config/RabbitMqConstants.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/config/PaymentRabbitConfig.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/PaymentReconcileMessage.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/domain/dtos/RefundProcessMessage.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/producers/PaymentCompletionPublisher.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/repositories/RefundRepository.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentService.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentReconciliationScheduler.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/RefundProcessingScheduler.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/consumers/PaymentReconciliationConsumer.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/consumers/RefundProcessingConsumer.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/producers/PaymentCompletionPublisherTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentReconciliationSchedulerTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/RefundProcessingSchedulerTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/consumers/PaymentReconciliationConsumerTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/consumers/RefundProcessingConsumerTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/controllers/PaymentControllerTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Chạy thử luồng thanh toán thực tế với VNPay.

- Session 006
  - Goal: Khắc phục triệt để 4 lỗi phát sinh trong luồng thanh toán thực tế với VNPay (Lỗi parse UUID 32-hex, lỗi Checksum QueryDR 13 trường, lỗi Duplicate Key khi IPN retry, và đồng bộ trạng thái Frontend).
  - Completed:
    - **Backend Fixes**:
      - Bổ sung helper `parseUuid(reference)` trong [`PaymentServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java) để tự động nhận diện và chèn hyphens cho chuỗi 32 hex format của VNPay, áp dụng cho cả `findPaymentForUpdate` và `findPaymentOrNull`.
      - Bổ sung helper `saveOrUpdateEvent` trong [`PaymentServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java) để cập nhật an toàn các `PaymentEvent` trước đó bị FAILED khi VNPay retry IPN, bảo vệ constraint `uk_payment_events_provider_dedupe`.
      - Cập nhật hàm `validSignature` trong [`VnpayPaymentStatusQueryGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayPaymentStatusQueryGateway.java) hỗ trợ cả chuẩn 13 trường (VNPay Sandbox không voucher) và 15 trường (có voucher), bổ sung debug logging.
    - **Frontend Fixes**:
      - Mở rộng kiểu `PaymentStatus` trong [`order.service.ts`](frontend/src/services/order.service.ts) chấp nhận giá trị `"SUCCESS"`.
      - Cập nhật [`PaymentResultPage.tsx`](frontend/src/routes/payment/PaymentResultPage.tsx) kiểm tra cả `SUCCEEDED` và `SUCCESS` để hiển thị thành công và xóa pending checkout.
    - **Unit & Integration Tests**:
      - Thêm test `verifiesStandard13FieldResponseWithoutPromotion` trong [`VnpayPaymentStatusQueryGatewayTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayPaymentStatusQueryGatewayTest.java) (4/4 tests passed).
      - Thêm test `vnpayIpnWith32HexReferenceResolvesPaymentAndSucceeds` trong [`PaymentResilienceIntegrationTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java) (7/7 tests passed).
      - Chạy toàn diện `./init.sh`: Backend tests đạt **272/272 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất không lỗi.
  - Commits: `e00c4d2` - fix(payment): resolve 32-hex UUID lookup, querydr checksum verification, and idempotent retry.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayPaymentStatusQueryGateway.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayPaymentStatusQueryGatewayTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java`
    - `frontend/src/services/order.service.ts`
    - `frontend/src/routes/payment/PaymentResultPage.tsx`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Chạy lại luồng kiểm thử thực tế trên browser/Sandbox để nghiệm thu giao dịch end-to-end.

- Session 007
  - Goal: Khắc phục lỗi lệch mã tham chiếu giao dịch (`merchant_txn_ref` vs `vnp_TxnRef`) trong luồng Late IPN tự động hoàn tiền (Refund) và bổ sung chẩn đoán logging.
  - Completed:
    - **Chẩn đoán nguyên nhân gốc**:
      - Lúc khởi tạo payment sang VNPay, cổng nhận mã `vnp_TxnRef` là chuỗi 32 hex không dấu `-` (`gateway.transactionReference(payment.getId())`), nhưng cột `payments.merchant_txn_ref` lại bị gán ngẫu nhiên `TBX_...`.
      - Khi IPN muộn đến, hệ thống tự động gọi `RefundGateway.refund` với `payment.merchant_txn_ref` (`TBX_...`). VNPay không tìm thấy giao dịch nên trả về gói tin lỗi, dẫn tới `VnpayRefundGateway.validSignature` không khớp và ném exception `invalid checksum`, che giấu mã lỗi thật.
    - **Backend Fixes**:
      - Đồng bộ `merchant_txn_ref` trong [`PaymentServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java): gán chính xác `gateway.transactionReference(payment.getId())` (đối với VNPay là 32-hex UUID, đối với MoMo là UUID đầy đủ) ngay khi khởi tạo payment, đảm bảo định danh đồng nhất giữa DB, Webhook và Refund API.
      - Cập nhật `gatewayRequest` và `reconcilePayment` trong `PaymentServiceImpl.java` sử dụng `payment.getMerchantTxnRef()` nhất quán.
      - Bổ sung logging chi tiết `rawResponse` và log cảnh báo khi chữ ký không khớp trong [`VnpayRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java).
    - **Unit & Integration Tests**:
      - Bổ sung unit test `vnpayCreatePaymentSetsMerchantTxnRefMatching32HexTransactionReference` trong [`PaymentResilienceServiceTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java) (14/14 passed).
      - Bổ sung integration test `testLatePaymentVNPay_passes32HexMerchantTxnRefToRefundGateway` trong [`PaymentResilienceIntegrationTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java) (8/8 passed).
      - Chạy toàn diện `./init.sh`: Backend đạt **274/274 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất 100%.
  - Commits: `1a09e51` - fix(payment): synchronize payment merchantTxnRef with gateway reference for refund.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/PaymentResilienceIntegrationTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Trên môi trường VNPay Sandbox, chức năng hoàn tiền (Refund API) có thể bị hạn chế cấp quyền tùy cấu hình merchant test của VNPay. Log `rawResponse` đã được bổ sung để hiển thị rõ mã phản hồi thật từ VNPay.
  - Next best step: Chạy thử luồng Late IPN thực tế để kiểm tra log phản hồi hoàn tiền từ cổng VNPay Sandbox.

- Session 008
  - Goal: Khắc phục lỗi xung đột `provider_transaction_id` giữa QueryDR định kỳ và IPN chính thức của cổng thanh toán khi giao dịch đang PENDING.
  - Completed:
    - **Chẩn đoán nguyên nhân**:
      - Khi user giữ chỗ và chờ quá hạn (Late IPN test), `PaymentReconciliationScheduler` kích hoạt QueryDR sang VNPay. Khi giao dịch đang chờ, VNPay trả về trạng thái PENDING kèm mã phiên tạm thời (ví dụ: `5529443`).
      - Khi user thanh toán xong sau 1 phút, VNPay gửi IPN chính thức với mã giao dịch thật (ví dụ: `15705923`).
      - Code cũ so sánh `payment.getProviderTransactionId() != outcome.providerTxnId()` mà không phân biệt trạng thái của Payment, dẫn đến việc IPN chính thức bị chặn nhầm là `CONFLICTING_RESULT`, return sớm mà không lưu `PaymentEvent`, không kích hoạt hoàn tiền Late IPN.
    - **Triển khai Backend**:
      - Cập nhật [`PaymentServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java): Điều kiện kiểm tra xung đột `provider_transaction_id` chỉ áp dụng khi payment đã ở trạng thái kết thúc (`isTerminalStatus`). Nếu payment đang `PENDING`, IPN chính thức được phép cập nhật `provider_transaction_id` chuẩn hóa.
      - Nếu thực sự có xung đột trên payment ở trạng thái kết thúc, lưu `PaymentEvent` trạng thái `FAILED` kèm mô tả lỗi và ghi audit log `PAYMENT_RESULT_CONFLICT`.
    - **Unit Tests & Verification**:
      - Thêm 2 unit tests trong [`PaymentResilienceServiceTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java):
        - `pendingPaymentWithProvisionalProviderTxnId_updatesToOfficialIpnTxnIdWithoutConflict`: Xác nhận payment `PENDING` được cập nhật mã IPN chính thức mà không bị báo lỗi.
        - `terminalPaymentWithDifferentProviderTxnId_isRejectedAsConflictingResult`: Xác nhận payment `SUCCEEDED` từ chối mã transaction khác và báo `CONFLICTING_RESULT`.
      - Chạy toàn diện `./init.sh`: Backend đạt **276/276 passed** (0 failures, 0 errors, 2 skipped), Frontend build pass 100%.
  - Commits: `caaec4d` - fix(payment): permit official provider txn id override during pending state and record conflict audit.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/PaymentResilienceServiceTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Tiến hành test lại luồng thanh toán thực tế Late IPN trên VNPay Sandbox.

- Session 009
  - Goal: Khắc phục hiện tượng Refund bị lưu status FAILED với mã `00 - Refund success`, lưu vết `provider_refund_id` khi hoàn tiền thất bại/đang xử lý, và loại trừ hiện tượng Poison Pill trong `NotificationConsumer`.
  - Completed:
    - **Chẩn đoán nguyên nhân**:
      - Trong `VnpayRefundGateway.java`: `mapResponseCode` chỉ map mã `00` và `01`, khiến các mã hoàn tiền đang được ngân hàng/VNPay thụ lý (`05` - VNPAY đang xử lý, `06` - VNPAY đã gửi yêu cầu sang ngân hàng) bị coi nhầm là `FAILED`. Đồng thời khi giao dịch thất bại tại cổng (mã `02`/`09`), code lưu nhầm `vnp_ResponseCode` (`00`) và `vnp_Message` (`Refund success`) thay vì mã lỗi từ chối của giao dịch (`TXN_STATUS_02`/`09`).
      - Trong `RefundServiceImpl.java`: Cột `provider_refund_id` chỉ được gán khi trạng thái là `SUCCESS`, làm mất dấu mã giao dịch đối soát của VNPay (`vnp_TransactionNo`) khi hoàn tiền đang xử lý hoặc thất bại.
      - Trong `NotificationConsumer.java`: Phương thức `recipientEmail` ném `IllegalStateException` khi không tìm thấy user id trong DB (tàn dư từ các lần test trước hoặc user bị xóa), dẫn tới việc Spring AMQP `ConditionalRejectingErrorHandler` reject nhưng liên tục redeliver message (Poison Pill), làm nghẽn queue và spam log.
    - **Triển khai Backend**:
      - Cập nhật [`VnpayRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java):
        - Bổ sung `vnp_TransactionStatus` `05` và `06` vào trạng thái `RefundStatus.PROCESSING`.
        - Khi giao dịch hoàn tiền bị cổng từ chối nhưng API tiếp nhận thành công (`vnp_ResponseCode == 00`), ánh xạ `responseCode` thành `TXN_STATUS_...` và thông báo lỗi rõ ràng.
      - Cập nhật [`RefundServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java): Luôn gán `provider_refund_id` nếu cổng trả về mã giao dịch hoàn tiền (`vnp_TransactionNo` hoặc `vnp_ResponseId`) cho mọi trạng thái (`SUCCESS`, `PROCESSING`, `FAILED`).
      - Cập nhật [`NotificationConsumer.java`](backend/src/main/java/com/ticketbox/api/module/notification/consumer/NotificationConsumer.java):
        - Chuyển `findRecipientEmail` thành trả về `Optional<String>`, ghi log warning và ack message an toàn khi không tìm thấy người dùng.
        - Thay thế Lombok `@RequiredArgsConstructor` bằng constructor tường minh, tuân thủ quy tắc dự án.
        - Purge sạch 2 message rác tồn đọng trong `notification.payment-failed.queue` trên RabbitMQ.
    - **Unit & Integration Tests**:
      - Cập nhật [`VnpayRefundGatewayTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java) kiểm thử đầy đủ các mã trạng thái `05`, `06`, `02`, `09` (15/15 passed).
      - Bổ sung test `testProcessRefund_gatewayFailedWithProviderRefundId` trong [`RefundServiceImplTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java) (6/6 passed).
      - Bổ sung tests `skipsPaymentFailedEmailWhenUserNotFound` và `skipsTicketIssuedEmailWhenUserNotFound` trong [`NotificationConsumerTest.java`](backend/src/test/java/com/ticketbox/api/module/notification/consumer/NotificationConsumerTest.java) (5/5 passed).
      - Chạy toàn diện `./init.sh`: Backend đạt **282/282 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất 100%.
  - Commits: `a2db0fb` - fix(payment): refine vnpay refund status mapping, persist provider refund id, and eliminate notification poison pill.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/notification/consumer/NotificationConsumer.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java`
    - `backend/src/test/java/com/ticketbox/api/module/notification/consumer/NotificationConsumerTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Tiến hành test lại luồng thanh toán thực tế Late IPN trên VNPay Sandbox.

- Session 010
  - Goal: Khắc phục lỗi checksum không hợp lệ khi VNPay trả về phản hồi trùng lặp không có chữ ký (`vnp_ResponseCode=94`, `vnp_Message=Request is duplicated`) và ngăn chặn gọi trùng API hoàn tiền khi lệnh đã được claim.
  - Completed:
    - **Chẩn đoán nguyên nhân**:
      - Khi nhận được Late IPN, cả hook after-commit và `RefundProcessingConsumer` (hoặc scheduler) cùng kích hoạt `processRefund`. Trong `RefundServiceImpl`, điều kiện kiểm tra claim `if (!Boolean.TRUE.equals(claimed) && refund.getStatus() != RefundStatus.PROCESSING)` do nghịch đảo logic đã cho phép thread thứ 2 tiếp tục gọi `gateway.refund` ngay cả khi refund đã ở trạng thái `PROCESSING`.
      - Khi VNPay nhận lệnh hoàn tiền thứ 2 với cùng `vnp_RequestId`, VNPay trả về mã `94` ("Request is duplicated") mà không kèm theo trường chữ ký `vnp_SecureHash`.
      - Trong `VnpayRefundGateway`, phương thức `validSignature` yêu cầu bắt buộc phải có `vnp_SecureHash`, dẫn đến việc ném `IllegalStateException("VNPay refund response has invalid checksum")`, làm gián đoạn consumer thay vì tiếp nhận mã `94` và giữ trạng thái `PROCESSING`.
    - **Triển khai Backend**:
      - Cập nhật [`VnpayRefundGateway.java`](backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java):
        - Chỉ yêu cầu bắt buộc và kiểm tra `vnp_SecureHash` khi phản hồi thành công (`vnp_ResponseCode == "00"`).
        - Đối với phản hồi lỗi hoặc trùng lặp từ cổng (`vnp_ResponseCode != "00"`), nếu không có `vnp_SecureHash`, ghi log warning và tiếp tục chuyển sang `mapResponseCode` (xử lý mã `94` thành `RefundStatus.PROCESSING`).
      - Cập nhật [`RefundServiceImpl.java`](backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java):
        - Sửa điều kiện claim: nếu `claimed == false` (tức không chuyển được trạng thái `REQUESTED -> PROCESSING`), lập tức return early không gọi cổng thanh toán lần 2.
    - **Unit Tests & Verification**:
      - Thêm 2 unit tests trong [`VnpayRefundGatewayTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java):
        - `handlesUnsignedDuplicateRequestResponseAsProcessing`: Xác nhận phản hồi trùng lặp không chữ ký (mã 94) được xử lý thành `PROCESSING` mà không ném exception.
        - `rejectsSuccessResponseWithoutSecureHash`: Xác nhận phản hồi thành công (mã 00) bắt buộc phải có chữ ký.
      - Thêm unit test `testProcessRefund_whenAlreadyClaimed_skipsGateway` trong [`RefundServiceImplTest.java`](backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java) xác nhận bỏ qua gọi gateway khi lệnh đã được claim.
      - Chạy toàn diện `./init.sh`: Backend đạt **285/285 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất 100%.
  - Commits: Sẵn sàng commit.
  - Files or artifacts updated:
    - `backend/src/main/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGateway.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/services/RefundServiceImpl.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/gateways/VnpayRefundGatewayTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/payment/services/RefundServiceImplTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Sẵn sàng commit và test thực tế.