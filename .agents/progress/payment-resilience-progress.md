# Task Progress

- File này được sử dụng để theo dõi một task được thực hiện qua nhiều Session.
- Mỗi khi được yêu cầu kết thúc một Session cần tổng kết các thông tin: Goal, Completed, Commits, Files or artifacts updated, Known risk or unresolved issue, Next best step.

## Task Description
- Name: Tái cấu trúc luồng thanh toán và cơ chế chống lỗi (Payment Resilience & Refactoring)
- Description:
  - Chuẩn hóa tài liệu hiện tại: Cập nhật `blueprint/specs/details/10-order-checkout.md` và `blueprint/specs/details/03-payment-idempotency.md` khớp với code đã triển khai thực tế trong codebase (`17-payment-resilience.md`, `PaymentServiceImpl`, `MomoGateway`, `VnpayGateway`).
  - Phân tích và đánh giá mô hình xử lý thanh toán mới theo `document.md` (tách cấu trúc bảng `payments`, `payment_events`, `refunds`; hội tụ xử lý IPN/Query/Return vào một hàm duy nhất; xử lý timeout URL, URL muộn, IPN muộn khi hold hết hạn; Worker chạy theo profile).
  - Đối chiếu và tinh chỉnh thiết kế để giữ lại tối đa các logic đã chạy ổn định hiện tại (Idempotency key với Redis, Circuit Breaker MoMo, ký URL VNPay tại chỗ, phân quyền, mapper).
  - Cập nhật / tạo mới Specs chuẩn hóa và cập nhật Database Design.
  - Lập Implementation Plan và Test Plan chi tiết.
- Goal: Hoàn thiện bộ Specs, Database Design và Plan triển khai thanh toán chống lỗi bền vững, giải quyết triệt để các ca biên (timeout, hold expired, out-of-order IPN) mà không làm gãy các luồng đang hoạt động tốt.
- Acceptance criteria:
  - [x] Phỏng vấn làm rõ yêu cầu với `interview-me`: chốt hoàn tiền tự động 100% khi hold hết hạn, tích hợp API Refund MoMo/VNPay.
  - [x] `03-payment-idempotency.md` và `10-order-checkout.md` được chuẩn hóa phản ánh đúng code hiện tại làm baseline.
  - [x] Đánh giá toàn diện kiến trúc đề xuất trong `document.md`, hoàn thiện đặc tả `17-payment-resilience.md`.
  - [x] Cập nhật Database Design: `payments.md`, tạo mới `payment_events.md`, `refunds.md`, cập nhật `database-design.md`.
  - [x] Ghi nhận Quyết định kiến trúc 04 vào `DECISIONS.md` và `.agents/DECISIONS/04-payment-resilience-events-and-refunds.md`.
  - [x] Lập Implementation Plan và Test Plan cụ thể, khả thi qua nhiều Session.
  - [x] Triển khai Phase 1: Flyway Migration V4, JPA Entities (`Payment`, `PaymentEvent`, `Refund`), Repositories, DTOs & Mappers.
  - [x] Triển khai Phase 2: Gateway Refund Clients (`MomoRefundGateway`, `VnpayRefundGateway`) và unit tests.
  - [x] Triển khai Phase 3: Refactor `PaymentServiceImpl` (hàm hội tụ `processPaymentResult`, khóa bi quan, xử lý Late IPN refund), `RefundService`, và Audit Logging.
  - [x] Triển khai Phase 4: Schedulers & Worker Profile (`@Profile({"api", "worker"})`, `PaymentReconciliationScheduler`, `RefundProcessingScheduler`, `PaymentReconciliationConsumer`, `RefundProcessingConsumer`).
  - [x] Triển khai Phase 5: Toàn bộ Unit Tests và kiểm thử tích hợp `./init.sh`.

## Session Logs

