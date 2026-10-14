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