# Thanh toán khi cổng lỗi: luồng tối giản cho TicketBox

> **Phạm vi:** tạo URL thanh toán và nhận IPN của MoMo/VNPay khi gặp timeout hoặc HTTP 5xx. Giữ ý tưởng ban đầu của người phát triển TicketBox: MoMo có CREATING, VNPay bắt đầu PENDING, kết quả chưa rõ thì giữ payment để đối soát. Đây là kế hoạch sửa code, chưa phải hành vi đang chạy. Ưu tiên cách xử lý dễ hiểu, có thể kiểm tra được; hoàn tiền tự động và các cơ chế tối ưu tải để ở giai đoạn sau.

## 1. Chỉ cần nhớ ba quy tắc

1. **Có URL chưa có nghĩa đã thanh toán.** PENDING chỉ nói TicketBox đã có URL hợp lệ để khách mở. Chỉ IPN hoặc query đã xác minh mới xác nhận tiền.
2. **Timeout/5xx không phải kết quả giao dịch.** Khi TicketBox gọi MoMo xin URL và không nhận phản hồi rõ ràng, ta chưa biết MoMo đã tạo yêu cầu hay chưa. Khi MoMo/VNPay gọi IPN mà TicketBox timeout/5xx, cổng có thể đã thu tiền rồi.
3. **Chỉ báo “đã nhận IPN” sau khi ghi DB thành công.** Nếu phản hồi bị mất sau khi ghi DB, IPN gửi lại phải an toàn và không phát vé hai lần.

| Thuật ngữ | Hiểu đơn giản |
| --- | --- |
| CREATING | Payment MoMo đã được TicketBox lưu nhưng chưa có URL để đưa cho khách. |
| PENDING | URL đã có; đang chờ kết quả thanh toán. |
| IPN | Cổng gọi vào server TicketBox để báo kết quả, độc lập với trình duyệt của khách. |
| Query/đối soát | TicketBox chủ động hỏi lại cổng khi thiếu kết quả IPN. |
| Circuit breaker | Tạm ngừng lời gọi **ra ngoài** tới cổng khi cổng đang lỗi liên tiếp; IPN gọi **vào** TicketBox không đi qua breaker này. |
| next_reconcile_at | Lịch hẹn worker kiểm tra payment tiếp theo; không phải trạng thái thanh toán. |

**Hai hướng gọi khác nhau:**

~~~text
Xin URL MoMo: TicketBox ──HTTP──> MoMo      (MoMo có thể trả 5xx/timeout)
Nhận IPN:     MoMo/VNPay ──HTTP──> TicketBox (TicketBox có thể trả 5xx/timeout)
Tạo URL VNPay: TicketBox tự ký URL tại chỗ  (không có HTTP xin URL từ VNPay)
~~~

## 2. Code hiện tại đang làm gì và thiếu gì?

| Vị trí | Hành vi hiện tại | Vì sao cần sửa |
| --- | --- | --- |
| [PaymentServiceImpl](../../backend/src/main/java/com/ticketbox/api/module/payment/services/PaymentServiceImpl.java) | Lưu **mọi** payment là PENDING trước khi tạo URL, sau đó mới lưu URL. | MoMo timeout/5xx để lại PENDING với URL rỗng, dù khách chưa thể thanh toán. |
| [MomoGateway](../../backend/src/main/java/com/ticketbox/api/module/payment/gateways/MomoGateway.java) | Mỗi lần gọi tạo requestId mới; lỗi HTTP và mã trả về bất kỳ bị gom thành MOMO_CREATE_FAILED/502. | Không thể thử lại đúng yêu cầu cũ và không phân biệt “bị từ chối chắc chắn” với “chưa biết kết quả”. Client HTTP chưa cấu hình timeout rõ ràng. |
| [IdempotencyService](../../backend/src/main/java/com/ticketbox/api/module/shared/idempotency/IdempotencyService.java) | Lưu lỗi AppException theo idempotency key. | Retry cùng key có thể nhận lại lỗi cũ; key mới bị payment PENDING cũ chặn. |
| [PaymentController](../../backend/src/main/java/com/ticketbox/api/module/payment/controllers/PaymentController.java) | MoMo IPN luôn trả 204 nếu service không ném exception, kể cả chữ ký sai, không tìm thấy payment hoặc sai số tiền. | Cổng có thể hiểu TicketBox đã nhận kết quả trong khi DB chưa ghi nhận. |
| [OrderInventoryServiceImpl](../../backend/src/main/java/com/ticketbox/api/module/order/services/OrderInventoryServiceImpl.java) | Xác nhận order khi còn HELD, chưa kiểm tra đã quá thời hạn giữ vé hay chưa. | IPN đến muộn vẫn có thể xác nhận order và phát vé. |
| [CheckoutPage](../../frontend/src/routes/audience/CheckoutPage.tsx) | Giả định response tạo payment luôn có URL và chuyển trình duyệt tới URL đó. | Cần hiển thị CREATING khi URL chưa có; không redirect với giá trị rỗng. |

Ngoài ra, chưa có worker hết hạn order, query bù hoặc refund trong payment/order module. Việc phát sự kiện sau khi commit DB hiện có thể thất bại riêng: payment đã SUCCEEDED nhưng sự kiện phát vé chưa được gửi; IPN lặp lại sẽ bị coi là đã xử lý. Cần giải quyết điểm này trước khi coi luồng IPN là hoàn chỉnh.

## 3. Luồng A — tạo URL thanh toán

### 3.1. VNPay

1. TicketBox tạo payment và tự ký URL VNPay tại chỗ.
2. Lưu URL cùng PENDING trong một transaction, sau đó trả URL cho trình duyệt.
3. Bước này **không có** trường hợp “VNPay trả 5xx/timeout”, vì chưa gọi VNPay. Nếu TicketBox tự lỗi, retry cùng idempotency key phải đọc lại payment/URL đã lưu hoặc tạo lại URL cho payment chưa hoàn tất.

### 3.2. MoMo

1. Lưu payment CREATING trước khi gọi cổng. Dùng mã payment làm orderId MoMo và dùng một requestId cố định cho lệnh create (có thể dùng chính mã payment nếu kiểm thử sandbox xác nhận phù hợp). URL đang rỗng; mọi lần thử lại phải gửi cùng payload.
2. Gọi MoMo create một lần. Cấu hình timeout HTTP rõ ràng; tài liệu MoMo yêu cầu tối thiểu **30 giây** cho lời gọi API. Có thể đặt circuit breaker Resilience4j tại lời gọi này.
3. Nếu MoMo trả thành công và có payUrl hợp lệ: lưu URL, chuyển PENDING trong cùng transaction, rồi mới trả URL cho khách.
4. Nếu MoMo **từ chối rõ ràng vì dữ liệu/yêu cầu không hợp lệ**: chuyển FAILED, hiển thị lỗi. Đây là kết luận từ phản hồi nghiệp vụ, không phải từ HTTP timeout.
5. Nếu MoMo trả **HTTP 5xx, timeout, mất kết nối hoặc breaker từ chối**: giữ CREATING, URL rỗng; API TicketBox trả payment_id và status=CREATING. Frontend hiển thị “Đang chuẩn bị thanh toán” và đọc lại trạng thái payment; không redirect, không tự tạo payment thứ hai.
6. Worker thử lại **chính request cũ, cùng payload và requestId** khi order còn hạn. Nếu nhận payUrl, chuyển PENDING; nếu MoMo báo đang xử lý, giữ CREATING và hẹn lại. Nếu order đã hết hạn, không cấp URL mới; chỉ đối soát giao dịch.

~~~mermaid
flowchart TD
  A[Tạo payment MoMo: CREATING, URL rỗng] --> B[Gọi create bằng requestId cố định]
  B -->|payUrl hợp lệ| C[Lưu URL và PENDING]
  B -->|Từ chối nghiệp vụ rõ ràng| D[FAILED]
  B -->|5xx hoặc timeout| E[Giữ CREATING, hẹn worker]
  E --> F{Order còn hạn?}
  F -->|Có| G[Thử lại cùng requestId để lấy URL]
  F -->|Không| H[Không cấp URL, đối soát tiền]
  G -->|Có payUrl| C
~~~

MoMo hỗ trợ retry bằng cùng requestId; phản hồi 7000 có thể nghĩa là yêu cầu cũ còn đang xử lý. API MoMo query **không trả payUrl**, nên không dùng query để “lấy lại URL”; query chỉ hỏi tình trạng giao dịch. [MoMo idempotency](https://developers.momo.vn/v3/docs/payment/api/result-handling/idempotency/), [query](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/), [timeout](https://developers.momo.vn/v3/docs/payment/onboarding/integration-process/). VNPay tạo URL tại merchant theo [tài liệu PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

## 4. Luồng B — nhận IPN và phản hồi cho cổng

1. Cổng xử lý thanh toán và gửi IPN tới TicketBox. Lúc này kết quả thanh toán ở cổng **không phụ thuộc** vào việc TicketBox có trả lời HTTP được hay không.
2. TicketBox kiểm tra chữ ký, provider, mã payment và số tiền. Sai thì **không đổi payment và không báo đã ghi nhận thành công**.
3. Với IPN hợp lệ, khóa payment/order và ghi kết quả trong transaction. Chỉ xử lý một lần: IPN trùng thì đọc kết quả đã ghi, không cộng vé hoặc phát sự kiện lần nữa.
4. Nếu order còn hạn và chưa xác nhận: thanh toán thành công chuyển payment SUCCEEDED, order CONFIRMED và xử lý vé. Nếu order đã quá hạn: ghi nhận **đã thu tiền**, không cấp vé; đưa vào danh sách cần hoàn tiền.
5. **Sau khi DB commit**, trả MoMo HTTP 204 trong 15 giây; trả VNPay JSON RspCode=00 (đã xử lý) hoặc 02 (đã xử lý trước đó). Nếu DB lỗi trước commit, không trả mã thành công: MoMo nhận lỗi HTTP; VNPay nhận RspCode=99 nếu TicketBox còn trả lời được.

~~~mermaid
flowchart TD
  A[Cổng gửi IPN] --> B{Chữ ký, payment và số tiền hợp lệ?}
  B -->|Không| C[Không đổi DB; phản hồi lỗi]
  B -->|Có| D[Khóa payment và order]
  D --> E{Giao dịch này đã xử lý?}
  E -->|Có, cùng kết quả| F[Không xử lý lần hai]
  F --> G[ACK trùng: MoMo 204; VNPay 02]
  E -->|Có, kết quả mâu thuẫn| S[Không ghi đè; cảnh báo đối soát]
  E -->|Chưa| H{Kết quả cổng?}
  H -->|Thất bại| I[Ghi payment FAILED]
  H -->|Thành công| J{Order còn hạn?}
  J -->|Có| K[Ghi SUCCEEDED, CONFIRMED và việc phát vé]
  J -->|Không| L[Ghi đã thu tiền; không phát vé; cần hoàn]
  I --> M{DB commit thành công?}
  K --> M
  L --> M
  M -->|Không| N[Rollback; không ACK thành công]
  M -->|Có| O[ACK: MoMo 204; VNPay 00]
  O --> P{Cổng nhận được ACK?}
  P -->|Có| Q[Kết thúc]
  P -->|Không, timeout hoặc 5xx| R[IPN có thể được gửi lại]
  R --> A
~~~

Nếu không có IPN gửi lại và payment vẫn chưa có kết quả, worker sẽ query cổng như phần dưới; query đã xác minh đi qua cùng quy tắc ghi DB và chống xử lý trùng.

### Nếu IPN bị timeout hoặc TicketBox trả 5xx thì sao?

| Thời điểm xảy ra lỗi | Trạng thái thực tế có thể có | Việc cần làm |
| --- | --- | --- |
| Trước khi DB commit | Chưa ghi kết quả | Không ACK thành công; IPN gửi lại hoặc worker query bù sẽ xử lý. |
| Sau khi DB commit nhưng trước khi cổng nhận ACK | DB đã ghi, cổng vẫn thấy timeout/5xx | IPN gửi lại phải trả lời “đã xử lý”, không ghi/phát vé lần hai. |
| Không có IPN gửi lại | TicketBox vẫn chưa biết kết quả | Worker query cổng; query timeout/5xx thì giữ trạng thái cũ và thử lại sau. |

VNPay mô tả retry IPN khi nhận RspCode không thành công hoặc IPN timeout; **không nên giả định mọi HTTP 5xx đều được retry**. Tài liệu MoMo yêu cầu 204 trong 15 giây nhưng không cam kết lịch retry IPN. Vì vậy query bù là lưới an toàn tối thiểu, không dựa hoàn toàn vào gateway gửi lại. [MoMo IPN](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/), [VNPay IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

**Return URL của trình duyệt chỉ để hiển thị trạng thái.** Nó không thay IPN hoặc query đã xác minh.

## 5. Phần triển khai tối thiểu

1. **Trạng thái và API:** thêm CREATING cho MoMo; VNPay tạo URL cùng PENDING; checkout_url cho phép rỗng khi CREATING. Retry cùng idempotency key đọc payment hiện tại, không cache lỗi mạng thành kết quả thanh toán.
2. **Gọi MoMo:** bảo đảm requestId create ổn định, có thể suy ra từ payment ID để khỏi thêm cột; timeout rõ ràng; Resilience4j circuit breaker ở lời gọi create và query. Trước mắt chưa cần nhiều breaker/bulkhead, retry nhiều tầng hoặc gateway_state.
3. **IPN:** xác minh rồi ghi DB idempotent, ACK đúng giao thức sau commit. Sửa MoMo endpoint để không trả 204 cho chữ ký/số tiền sai. Bảo đảm bước phát vé không mất sau khi payment đã commit; có thể đưa vào cùng transaction nếu phù hợp, hoặc dùng một bản ghi sự kiện bền vững.
4. **Một worker đối soát payment:** dùng next_reconcile_at để (a) thử lại create MoMo cùng requestId khi CREATING và order còn hạn; (b) query MoMo/VNPay khi PENDING thiếu IPN; (c) kiểm tra kết quả tài chính của CREATING đã hết hạn. Query lỗi/timeout/5xx thì hẹn lại, không đổi thành FAILED.
5. **Hết hạn và hoàn tiền:** worker hết hạn order nhả vé/quota. IPN/query thành công sau hạn không được phát vé. Giai đoạn đầu chỉ cần **ghi và cảnh báo khoản cần hoàn tiền để xử lý thủ công**; tự động gọi API refund là bước sau. Không được âm thầm bỏ qua khoản đã thu.

~~~text
MoMo bình thường: CREATING → PENDING (đã lưu URL) → SUCCEEDED/FAILED (IPN hoặc query)
VNPay bình thường: PENDING (đã lưu URL) → SUCCEEDED/FAILED (IPN hoặc query)
Timeout/5xx xin URL MoMo: giữ CREATING; timeout/5xx IPN: giữ kết quả DB đã commit hoặc chờ query bù.
~~~

Ngoại lệ: nếu cổng xác minh đã thu tiền khi payment vẫn CREATING (TicketBox chưa từng lưu URL), vẫn phải ghi nhận khoản tiền, **không cấp vé** và đưa vào danh sách cần hoàn; không ép giao dịch đi qua PENDING chỉ để giữ sơ đồ đẹp.

## 6. Bốn trường hợp bắt buộc kiểm thử trước khi dùng thật

| Tình huống | Kết quả mong đợi |
| --- | --- |
| MoMo create timeout hoặc trả 5xx sau khi nhận request | Payment CREATING, không URL; retry cùng requestId nhận lại URL hoặc tiếp tục chờ, không tạo attempt mới. |
| IPN hợp lệ nhưng DB lỗi trước commit | Không ACK thành công; payment chưa đổi; gửi lại/query bù có thể xử lý. |
| DB commit thành công nhưng ACK IPN bị mất | IPN lặp lại được ACK; order/vé chỉ xử lý một lần. |
| IPN thành công đến sau hạn giữ vé | Ghi đã thu tiền; order không được xác nhận, không cấp vé; có mục cần hoàn tiền. |

**Giới hạn có chủ ý:** tài liệu này chưa thiết kế hoàn tiền tự động, nhiều tầng retry hay đối soát tài chính toàn diện. Nó giữ ba điều thiết yếu: không coi timeout là thất bại thanh toán, không báo đã nhận IPN trước khi lưu thành công, và không mất dấu khoản tiền đã thu.
