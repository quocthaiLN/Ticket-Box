# QUYẾT ĐỊNH 04: KIẾN TRÚC THANH TOÁN CHỐNG LỖI (PAYMENT RESILIENCE, PAYMENT EVENTS VÀ AUTOMATED REFUND)

## 1. What is the decision? (Quyết định là gì?)

- Tách rời hoàn toàn 3 thực thể dữ liệu trong phân hệ thanh toán:
  1. `payments`: Đại diện cho một lượt cố gắng thanh toán (attempt) gắn với `order_id`, sở hữu `merchant_txn_ref` (UNIQUE) cố định để làm tham chiếu cho toàn bộ vòng đời giao dịch, hỗ trợ khóa lạc quan với cột `version` (`@Version`).
  2. `payment_events`: Nhật ký sự kiện bất biến (append-only audit log) cho mọi tín hiệu nhận được từ cổng (`IPN`, `RETURN_URL`, `QUERY_API`, `INTERNAL`), sử dụng `dedupe_key` (UNIQUE cùng `provider`) để loại trừ trùng lặp ngay từ cấp độ cơ sở dữ liệu.
  3. `refunds`: Quản lý các giao dịch hoàn tiền độc lập, có `idempotency_key` duy nhất và liên kết với `payments`, theo dõi trạng thái (`REQUESTED`, `PROCESSING`, `SUCCESS`, `FAILED`) và tích hợp trực tiếp với API hoàn tiền của cổng thanh toán (MoMo Refund, VNPay Refund).
- Tách bạch trạng thái thanh toán (`PaymentStatus.SUCCESS`) và trạng thái đơn hàng (`OrderStatus.CONFIRMED` / `EXPIRED`). Tiền đã thu không đồng nghĩa với vé đã phát.
- Hội tụ toàn bộ luồng xử lý kết quả thanh toán (IPN, ReturnUrl query, Reconciliation worker query) về một hàm xử lý duy nhất (`processPaymentResult`) có khóa bi quan `SELECT ... FOR UPDATE` theo `merchant_txn_ref`.
- Xử lý triệt để ca "Late IPN" (IPN thành công đến sau khi thời hạn giữ vé `hold_expires_at` của đơn đã hết hạn):
  - Tuyệt đối **không cấp vé** và **không chuyển đơn sang CONFIRMED**.
  - Ghi nhận `payments.status = SUCCESS`.
  - Tự động tạo bản ghi `refunds` với `idempotency_key = "LATE_REFUND_" + merchant_txn_ref` và gọi API Refund tự động của cổng thanh toán.
- Worker đối soát và hết hạn được quản trị tách biệt bằng cơ chế Spring Profile (`@Profile("worker")` hoặc thuộc tính cấu hình `app.scheduling.enabled`), cho phép triển khai chạy chung (monolith) hoặc tách thành container worker riêng biệt trong môi trường phân tán.

## 2. Why I choose it? (Vì sao lựa chọn phương án này?)

1. **Khắc phục triệt để các ca lỗi biên mạng**:
   - URL Gateway timeout: `payments` giữ `INITIATED`, cho phép worker đối soát bù qua `merchant_txn_ref` mà không tạo attempt mù quáng.
   - Response URL đến muộn: Kiểm soát bằng khóa lạc quan `version`, từ chối cập nhật nếu payment đã chuyển sang `FAILED` hoặc `EXPIRED`.
   - IPN gửi lại nhiều lần / timeout: `payment_events` chặn trùng lặp bằng unique `(provider, dedupe_key)`, trả ACK idempotent ngay lập tức.
   - IPN đến sau khi hold hết hạn: Không làm gãy tính toàn vẹn của kho vé (không bán quá số lượng - overselling), đồng thời không làm thất thoát tiền của khách (tự động hoàn tiền minh bạch).
2. **Khả năng đối soát và truy vết kiểm toán tuyệt đối**: Lưu nguyên văn raw payload và chữ ký của mọi sự kiện vào `payment_events`, giúp việc tra cứu lịch sử và giải quyết tranh chấp với cổng thanh toán trở nên dễ dàng.
3. **Kế thừa và bảo tồn các luồng đã chạy ổn định**:
   - Vẫn sử dụng Redis `IdempotencyService` cho API `POST /payments` của client.
   - Vẫn giữ cơ chế tự ký URL VNPAY tại chỗ tốc độ cao và Circuit Breaker Resilience4j cho MoMo.

## 3. Others solution but I don't choose and reason (Các phương án khác bị bác bỏ)

| Phương án | Lý do không chọn |
|---|---|
| **Giữ nguyên 1 bảng `payments` và cờ `refund_required`** | Không có lịch sử webhook (bị ghi đè), không có mã dedupe độc lập, hoàn tiền phải xử lý thủ công bằng cơm, dễ xảy ra race-condition giữa IPN và ReturnUrl. |
| **Tái cấp hold (Re-hold) và phát vé khi IPN đến muộn nếu ghế còn trống** | Gây race-condition cực kỳ phức tạp với các phiên checkout khác đang tranh chấp ghế; trải nghiệm người dùng xấu vì người dùng tưởng đơn đã hủy sau countdown; vi phạm tính nhất quán của thời hạn giữ chỗ. |
| **Gộp chung trạng thái `Payment` và `Order`** | Khi tiền đã thu nhưng vé không thể phát (do hết hạn hoặc lỗi hệ thống), việc gộp chung khiến hệ thống không thể biểu diễn đúng thực tế tài chính, dẫn đến việc hoặc mất tiền của khách hoặc phát vé sai quy định. |
