# Đặc tả: Rate Limiting & Anti-bot

**Hệ thống:** Ticket Box  
**Trạng thái:** Đặc tả thiết kế  
**Phạm vi:** HTTP API thuộc auth, public API của catalog, order và payment

## 1. Mô tả thiết kế và quyết định

### 1.1. Mục tiêu và phạm vi

Cơ chế gồm hai lớp bảo vệ:

1. **Nginx** giới hạn request theo **IP và nhóm API** đối với auth, public catalog, order, payment. Một giới hạn leaky bucket theo IP trên **toàn bộ ứng dụng** làm phẳng burst trước khi request tới Spring Boot.
2. **Spring Boot Rate Limit Filter + Redis** giới hạn theo **User ID** trên các API có người dùng đã được xác thực thuộc auth, order, payment. Bộ đếm dùng **sliding window log**, được cập nhật bằng một **Lua script** chạy trong Redis.

Hai lớp kiểm tra độc lập: request phải qua Nginx trước, sau đó mới tới giới hạn theo User ID. Catalog public chỉ chịu các chính sách Nginx đã nêu; không tự suy ra User ID từ request công khai.

### 1.2. Quy ước tại Nginx

| Chính sách | Khóa giới hạn | Áp dụng | Vai trò |
| --- | --- | --- | --- |
| Toàn ứng dụng | IP client | Mọi request đi qua Nginx | Giảm burst tổng thể bằng `limit_req` kiểu leaky bucket. |
| Theo nhóm API | IP client + nhóm API đã chuẩn hóa | Auth, public catalog, order, payment | Chặn một IP gọi dồn vào một nhóm API. |

- Nhóm API là tên route ổn định do cấu hình xác định (`auth`, `catalog-public`, `order`, `payment`), không dùng URI thô chứa ID hoặc query string. Danh sách route cụ thể và ngưỡng `rate`/`burst` là cấu hình vận hành, cần điền trước khi bật enforcement; đặc tả này không tự đặt các con số chưa được chốt.
- Cấu hình `limit_req` cho toàn ứng dụng **không dùng `nodelay`** để các request trong `burst` được trì hoãn và luồng được làm phẳng. `burst` là số request vượt tốc độ trung bình có thể chờ, không phải quota bổ sung theo mỗi giây. Request vượt khả năng chờ bị từ chối với HTTP `429` qua `limit_req_status 429`.
- Khi một `location` định nghĩa `limit_req` riêng cho nhóm API, phải áp dụng **cả giới hạn toàn ứng dụng lẫn giới hạn nhóm API** tại location đó: Nginx chỉ kế thừa các `limit_req` từ cấp trên nếu cấp hiện tại không định nghĩa `limit_req` nào. [Tài liệu Nginx `limit_req`](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html).
- IP dùng để giới hạn phải là IP client đã được xác thực qua chuỗi proxy tin cậy. Nếu có load balancer/CDN phía trước, chỉ tin `X-Forwarded-For` từ các địa chỉ proxy được cấu hình bằng `set_real_ip_from`; không tin header do client trực tiếp gửi. [Tài liệu Nginx Real IP](https://nginx.org/en/docs/http/ngx_http_realip_module.html).
- Nginx trả `429` ngay tại biên khi vượt giới hạn; request đó không được chuyển đến Spring Boot. Các instance Nginx độc lập không tự chia sẻ trạng thái `limit_req`; cấu hình triển khai nhiều gateway cần tính đến phạm vi giới hạn này.

### 1.3. Quy ước tại Spring Boot và Redis

**Vị trí:** `RateLimitFilter` chạy sau bước xác thực đã tạo `SecurityContext`, và trước controller/xử lý nghiệp vụ. Filter chỉ lấy User ID từ principal tin cậy; không lấy từ body, query, path hoặc header `X-User-ID` do client cung cấp. Các request chưa xác thực, như login hoặc đăng ký, không có User ID để áp chính sách Redis này và vẫn chịu giới hạn IP tại Nginx.

**Định dạng key:** `ratelimit:{dimension}:{identity}:{resource}`.

| Thành phần | Quy ước |
| --- | --- |
| `dimension` | Giá trị `user` cho lớp Redis theo User ID. |
| `identity` | User ID nội bộ từ principal đã xác thực; mã hóa/chuẩn hóa nhất quán để không chứa dấu phân cách của key. |
| `resource` | Mã chính sách ổn định theo thao tác hoặc nhóm API, ví dụ `auth:refresh`, `order:create`, `payment:initiate`; không dùng URL thô, order ID hay payment ID. |

Ví dụ: `ratelimit:user:42:order:create`. Mỗi cặp `(User ID, resource)` có một cửa sổ độc lập. Không đặt token, email hoặc dữ liệu thanh toán vào key. Bảng ánh xạ method + route chuẩn hóa → `resource`, cùng `limit` và `windowMs`, phải được quản lý tập trung để tất cả instance Spring Boot dùng cùng chính sách.

**Dữ liệu Redis:** một sorted set cho mỗi key. Score là Unix timestamp theo mili giây; member có dạng `timestamp:UUID`, ví dụ `1790000000123:550e8400-e29b-41d4-a716-446655440000`. UUID làm mỗi lượt truy cập là một member riêng, kể cả khi nhiều request cùng mili giây. Key được đặt TTL ít nhất bằng độ dài cửa sổ và làm mới khi ghi lượt được cho phép, để dữ liệu hết hạn tự được dọn.

**Quy tắc sliding window log:** với thời điểm `now` và cửa sổ `W`, chỉ tính các log có timestamp trong khoảng **`(now - W, now]`**. Một Lua script nhận key trong `KEYS` và các tham số chính sách trong `ARGV`, rồi thực hiện nguyên tử:

1. Lấy thời gian chuẩn của Redis; tính mốc `now - W`.
2. Xóa các member có score `<= now - W` bằng `ZREMRANGEBYSCORE`.
3. Đếm member còn lại bằng `ZCARD`.
4. Nếu count `< limit`, thêm member `now:UUID` bằng `ZADD`, đặt/cập nhật TTL, trả `allowed=true`, `remaining=limit-count-1`.
5. Nếu count `>= limit`, không ghi thêm log; trả `allowed=false` và thời gian chờ ước tính từ timestamp cũ nhất còn trong cửa sổ.

Script phải dùng đúng key được truyền qua `KEYS`, tránh tạo tên key ẩn trong Lua; UUID được tạo cho từng request ở ứng dụng và truyền qua `ARGV`. Redis thực thi script một cách nguyên tử, giúp các instance ứng dụng không cùng lúc cho vượt quota khi có request cạnh tranh. [Redis Lua scripting](https://redis.io/docs/latest/develop/interact/programmability/eval-intro/), [Redis `EVAL`](https://redis.io/docs/latest/commands/eval/). `ZREMRANGEBYSCORE` có biên bao gồm mốc trên, đúng với quy ước cửa sổ ở trên. [Redis `ZREMRANGEBYSCORE`](https://redis.io/docs/latest/commands/zremrangebyscore/).

**Phản hồi:** request vượt giới hạn Redis nhận HTTP `429 Too Many Requests`, thông báo lỗi chuẩn của Ticket Box và `Retry-After` làm tròn lên giây từ thời gian chờ script trả về. Request bị từ chối không đi tới controller và không tạo order/payment. Một request được cho phép sẽ chiếm một lượt ngay cả khi xử lý nghiệp vụ sau đó trả lỗi; cơ chế này đếm **request**, không đếm giao dịch thành công.

**Quan sát vận hành:** ghi metric cho số request được cho phép, bị từ chối và bị trì hoãn tại Nginx; số request bị từ chối theo `resource` tại Filter; độ trễ/lỗi Redis. Không ghi raw User ID, IP hoặc member `timestamp:UUID` vào metric có cardinality lớn. Chính sách khi Redis không sẵn sàng cần được chốt riêng trước triển khai vì quyết định cho qua hay chặn sẽ ảnh hưởng trực tiếp đến khả dụng của order/payment.

### 1.4. Ranh giới của thiết kế anti-bot

Giới hạn theo IP và User ID làm giảm tốc độ abuse, nhưng không tự nhận diện bot phân tán qua nhiều IP hoặc nhiều tài khoản. CAPTCHA, device fingerprint, WAF và các chính sách theo tài khoản chưa thuộc các quyết định của đặc tả này.

## 2. Actor

| Actor | Vai trò |
| --- | --- |
| Client (người dùng hoặc bot) | Gửi HTTP request đến Ticket Box. |
| Nginx | Xác định IP tin cậy, áp hai chính sách IP/toàn ứng dụng và IP/nhóm API, trì hoãn hoặc trả `429`. |
| Spring Security | Xác thực request, cung cấp principal và User ID tin cậy. |
| Rate Limit Filter | Chọn chính sách theo route, tạo UUID, gọi Lua script và quyết định cho request đi tiếp hoặc trả `429`. |
| Redis | Lưu sliding window log và thực thi thao tác kiểm tra/cập nhật nguyên tử. |
| Controller/Service | Xử lý nghiệp vụ chỉ sau khi request vượt qua các lớp giới hạn. |

## 3. Luồng chính

1. Client gửi request tới Nginx. Nginx xác định IP client từ kết nối hoặc từ proxy đã được tin cậy.
2. Nginx áp leaky bucket toàn ứng dụng theo IP. Nếu request nằm trong burst, Nginx có thể trì hoãn để làm phẳng tải; nếu vượt ngưỡng, Nginx trả `429` và kết thúc luồng.
3. Nếu route thuộc auth, public catalog, order hoặc payment, Nginx tiếp tục áp giới hạn IP theo nhóm API. Nếu vượt ngưỡng, Nginx trả `429`; nếu không, chuyển request tới Spring Boot.
4. Spring Security xác thực request. Với route có chính sách User ID và principal hợp lệ, `RateLimitFilter` lấy User ID, ánh xạ route sang `resource` và tạo key `ratelimit:user:{userId}:{resource}`.
5. Filter gọi Lua script trong Redis với key, `limit`, `windowMs` và UUID của request. Script xóa log hết hạn, đếm log trong cửa sổ, rồi quyết định và ghi log mới nếu được phép.
6. Nếu script từ chối, Filter trả `429` kèm `Retry-After` và kết thúc luồng. Nếu cho phép, Filter chuyển request tới controller/service.
7. Với route chưa xác thực hoặc không có chính sách User ID, Filter không áp giới hạn Redis theo User ID; các giới hạn Nginx vẫn có hiệu lực.

**Điều kiện kiểm chứng:** request vượt ngưỡng Nginx không tới Spring Boot; request vượt ngưỡng Redis không tới controller; hai request đồng thời của cùng `(User ID, resource)` không thể cùng vượt giới hạn; log hết cửa sổ được loại trước khi đếm; hai request cùng timestamp vẫn có hai member riêng.


## 5. Kịch bản lỗi

- Redis rate limit lỗi: fail-closed cho endpoint checkout/payment, fail-open có kiểm soát cho catalog tùy chính sách.
- User dùng nhiều IP: áp thêm limit theo user id sau đăng nhập.
- IP dùng chung NAT: tránh ngưỡng quá thấp ở catalog để không chặn nhầm.
- Bot vượt CAPTCHA/limit: backend vẫn có per-user limit và idempotency bảo vệ.

## 6. Ràng buộc nghiệp vụ và kỹ thuật

- Rate limit phải chạy trước logic DB nặng.
- Endpoint mua vé có limit theo user id và route.
- Endpoint public catalog có limit rộng hơn và tận dụng cache.
- Không dùng rate limit thay thế kiểm tra nghiệp vụ.
- Cấu hình limit phải có thể thay đổi theo chiến dịch mở bán.

## 7. Tiêu chí chấp nhận

- Spam checkout bị chặn trước khi vào database.
- Request vượt ngưỡng nhận 429.
- Người dùng thật vẫn xem catalog được trong ngưỡng hợp lý.
- Database không bị request rác dội thẳng khi mở bán.
