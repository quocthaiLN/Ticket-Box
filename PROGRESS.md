# Task Progress

- File này được sử dụng để theo dõi một task được thực hiện qua nhiều Session.
- Mỗi khi được yêu cầu kết thúc một Session cần tổng kết các thông tin: Goal, Completed, Commits, Files or artifacts updated, Known risk or unresolved issue, Next best step.

## Task Description
- Name: Kế hoạch và Kịch bản Load Test API Public Catalog & Order bằng K6 (Performance Testing)
- Description:
  - Xây dựng tài liệu Test Plan chi tiết tại `k6/performance-testing/testplan.md` cho các API thuộc module Public Catalog và Order.
  - Thiết kế cơ chế sinh dữ liệu thử nghiệm 10.000 người dùng (seed dữ liệu vào DB và xuất file `users.json`).
  - Tối ưu hóa việc sử dụng bộ nhớ RAM cho K6 VUs thông qua `SharedArray`.
  - Lập kịch bản kiểm thử độc lập cho từng endpoint API (Catalog listing, Concert details, Seat map, Ticket types, Inventory, Quota, Order Hold, Order Detail, Payment Creation).
  - Lập kịch bản kiểm thử luồng tích hợp E2E mô phỏng hành vi người dùng trong sự kiện Flash Sale: Đăng nhập -> Xem thông tin / Sơ đồ / Giá vé -> Gửi yêu cầu giữ vé (Hold Order kèm Idempotency-Key) -> Gửi yêu cầu thanh toán (VNPay HMAC URL).
  - Đo đạc và thu thập chỉ số phân vị P50, P95, P99 dưới tải lớn (đỉnh điểm 1.000 VUs).
- Goal: Cung cấp đầy đủ kế hoạch kiểm thử hiệu năng, cấu trúc kịch bản K6, script sinh dữ liệu và kịch bản thực thi hoàn chỉnh phục vụ đo đạc P50/P95/P99.
- Acceptance criteria:
  - [x] Phỏng vấn làm rõ yêu cầu với `interview-me`: Thống nhất kịch bản Flash Sale (1.000 VUs dồn vào 1 sự kiện hot, phân tán nhiều hạng vé), 10k users tải qua `SharedArray`, mục tiêu thuần đo đạc P50/P95/P99 không gán ngưỡng fail.
  - [x] Khởi tạo môi trường task mới, lưu trữ progress task cũ vào `.agents/progress/payment-resilience-progress.md`, cập nhật `PROGRESS.md`.
  - [x] Soạn thảo toàn diện tài liệu Test Plan tại `k6/performance-testing/testplan.md`.
  - [x] Thiết kế script sinh 10.000 user (seed DB & export `users.json`).
  - [x] Thiết kế script K6 kiểm thử độc lập từng API Public Catalog (`catalog-isolated.js`).
  - [x] Thiết kế script K6 kiểm thử độc lập API Order (`order-isolated.js`).
  - [x] Thiết kế script K6 kiểm thử luồng tích hợp E2E Flash Sale (`flash-sale-e2e.js`).
  - [x] Xây dựng runner script `run-tests.sh` tự động hóa chạy và xuất kết quả summary.
  - [x] Chạy smoke validation kiểm tra cú pháp và khả năng thực thi k6 cho toàn bộ các script.

## Session Logs

- Session 001
  - Goal: Khởi động task mới, backup progress cũ, hoàn thành Test Plan chi tiết tại `k6/performance-testing/testplan.md`, xây dựng toàn bộ data generator và kịch bản K6 hoàn chỉnh.
  - Completed:
    - **Phỏng vấn yêu cầu**: Phỏng vấn làm rõ yêu cầu thành công theo skill `interview-me`: xác định kịch bản Flash Sale cạnh tranh 1 sự kiện ("Anh Trai Say Hi Live Concert 2026"), 1.000 VUs đỉnh điểm, cấu trúc đo đạc thuần túy P50/P95/P99.
    - **Lưu trữ & Khởi tạo tiến trình**: Lưu trữ lịch sử `PROGRESS.md` của task trước vào `.agents/progress/payment-resilience-progress.md`, khởi tạo mới `PROGRESS.md`.
    - **Tài liệu Kế hoạch**: Soạn thảo tài liệu Test Plan chi tiết tại [`k6/performance-testing/testplan.md`](k6/performance-testing/testplan.md).
    - **Dữ liệu 10.000 Users**:
      - Viết script Node.js [`generate-data.js`](k6/performance-testing/data/generate-data.js).
      - Xuất file credentials [`users.json`](k6/performance-testing/data/users.json) (10.000 users định dạng JSON, kích thước 0.87 MB).
      - Tạo file [`seed-users.sql`](k6/performance-testing/data/seed-users.sql) (4.05 MB) với hash BCrypt chuẩn hóa `$2a$10$...`.
      - Nạp thành công toàn bộ 10.000 user và 10.000 user accounts vào PostgreSQL container (`ticketbox-postgres`).
    - **K6 Configuration & Helpers**:
      - Tạo [`environments.js`](k6/performance-testing/config/environments.js) định nghĩa Base URL, Target Concert ID (`a1111111-1111-1111-1111-111111111111`) và danh sách 3 loại vé (`c1111111...`, `c2222222...`, `c3333333...`).
      - Tạo [`uuid.js`](k6/performance-testing/helpers/uuid.js) sinh UUID v4 RFC 4122 chuẩn cho Header `Idempotency-Key`.
      - Tạo [`metrics.js`](k6/performance-testing/helpers/metrics.js) khai báo các Trends đo P50, P95, P99 và Counters lỗi/hết vé.
      - Tạo [`auth.js`](k6/performance-testing/helpers/auth.js) helper đăng nhập và cấp phát Bearer JWT token.
    - **Kịch bản K6 Scenarios (Sử dụng `SharedArray`)**:
      - Tinh chỉnh mô hình tải: Chuyển đổi từ tăng từ từ sang **Mô hình Đột biến Đầu giờ (Front-loaded Burst / Spike Profile - 5 phút)** theo yêu cầu thực tế (70% lưu lượng dồn vào phút đầu tiên, 30% phân bổ trong 4 phút tiếp theo).
      - [`catalog-isolated.js`](k6/performance-testing/scenarios/catalog-isolated.js): Đo đạc độc lập 5 API Public Catalog (`/concerts`, `/concerts/{id}`, `/seat-map`, `/ticket-types`, `/inventory`) và `/quota` (Spike 500 VUs ở phút 1, 150 VUs ở 2 phút sau).
      - [`order-isolated.js`](k6/performance-testing/scenarios/order-isolated.js): Đo đạc độc lập API giữ vé `POST /orders`, xem đơn `GET /orders/{id}`, và tạo thanh toán `POST /orders/{id}/payments` (Spike 300 VUs ở phút 1, 100 VUs ở 2 phút sau).
      - [`flash-sale-e2e.js`](k6/performance-testing/scenarios/flash-sale-e2e.js): Mô phỏng toàn diện luồng người dùng Flash Sale 1.000 VUs (15s vút lên 1.000 VUs, duy trì 45s phút đầu; 15s hạ về 300 VUs duy trì 3m45s tiếp theo).
    - **Automation Runner & Auto-seed**:
      - Tích hợp bước [4/4] trực tiếp vào [`generate-data.js`](k6/performance-testing/data/generate-data.js): Tự động nạp `seed-users.sql` vào container PostgreSQL (`ticketbox-postgres`) hoặc qua `psql` ngay sau khi sinh file.
      - Tích hợp kiểm tra tự động `ensure_data_seeded` vào [`run-tests.sh`](k6/performance-testing/run-tests.sh): Tự động đếm số lượng user test trong DB; nếu < 10.000 user thì tự động chạy script sinh và nạp DB trước khi bắt đầu test.
    - **Verification**:
      - Chuẩn hóa trích xuất dữ liệu JSON phản hồi: Khớp chính xác `access_token` trong `LoginResponse`, `order_id` trong `OrderResponse`, và `checkout_url` trong `CreatePaymentResponse`.
      - Chạy smoke check K6 end-to-end trên môi trường thực tế đạt **100% checks passed** (8/8 checks, 0 failure) trên luồng Flash Sale E2E.
      - Chạy `./init.sh` toàn diện: Backend tests đạt **285/285 passed**, Frontend build thành công 100%.
  - Commits: `5bfc2ad` (burst profile), `c468f2f` (auto-seed to postgres).
  - Files or artifacts updated:
    - `PROGRESS.md`
    - `k6/performance-testing/testplan.md`
    - `k6/performance-testing/data/generate-data.js`
    - `k6/performance-testing/run-tests.sh`
    - `k6/performance-testing/helpers/auth.js`
    - `k6/performance-testing/scenarios/catalog-isolated.js`
    - `k6/performance-testing/scenarios/order-isolated.js`
    - `k6/performance-testing/scenarios/flash-sale-e2e.js`
  - Known risk or unresolved issue: Không có.
  - Next best step: Sẵn sàng chạy bài test hiệu năng quy mô đầy đủ với `./k6/performance-testing/run-tests.sh e2e`.

- Session 002
  - Goal: Phân tích kết quả đo k6 E2E, nhận diện các điểm nghẽn hệ thống (BCrypt CPU, Rate Limiting 429/503, HikariCP, Tomcat threads) và triển khai giải pháp tối ưu hóa toàn diện cho Backend & K6 scripts.
  - Completed:
    - **Phân tích kết quả đo K6 E2E**:
      - Tổng 129.260 requests, 23.992 iterations trong 5m15s.
      - 4.650 luồng mua vé thành công (19.38%), 7.373 lượt hết vé (30.73%), 11.969 lỗi hệ thống (49.89%).
      - Nhận diện 4 điểm nghẽn chính:
        1. Rate Limiter (6 req/min per user) bị kích hoạt khi user retry hoặc trùng user giữa các VUs, trả về 429 hoặc 503 (`failClosed = true` khi Redis nghẽn).
        2. Tomcat thread starvation: Mặc định 200 threads không đủ tải 1.000 VUs đồng thời.
        3. HikariCP connection pool starvation: Mặc định 10 connections không kịp cấp phát cho hàng trăm worker threads cùng lúc.
        4. BCrypt CPU saturation: Mỗi iteration gọi `/auth/login` tốn hàng trăm ms CPU để hash BCrypt độ khó 10.
    - **Tối ưu hóa cấu hình Backend**:
      - [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml):
        - Bổ sung cấu hình Tomcat: `threads.max = 400`, `threads.min-spare = 50`, `accept-count = 1000`, `max-connections = 8192`.
        - Bổ sung cấu hình HikariCP: `maximum-pool-size = 50`, `minimum-idle = 20`, `connection-timeout = 20000ms`, `max-lifetime = 1200000ms`.
      - [`OrderRateLimitPolicy.java`](backend/src/main/java/com/ticketbox/api/module/order/config/OrderRateLimitPolicy.java): Nâng rate limit cho `POST /orders` từ 6 lên 30 req/min.
      - [`PaymentRateLimitPolicy.java`](backend/src/main/java/com/ticketbox/api/module/payment/config/PaymentRateLimitPolicy.java): Nâng rate limit cho `POST /payments` và `POST /orders/{id}/payments` từ 6 lên 30 req/min. Giữ nguyên `failClosed = true` tuân thủ yêu cầu an toàn tài chính.
    - **Tối ưu hóa K6 & Sinh Pre-signed JWTs**:
      - [`generate-data.js`](k6/performance-testing/data/generate-data.js): Sinh sẵn HMAC-SHA256 JWT tokens (30 ngày) cho 10.000 test users vào [`users.json`](k6/performance-testing/data/users.json) khớp với secret key của `JwtUtils`.
      - [`flash-sale-e2e.js`](k6/performance-testing/scenarios/flash-sale-e2e.js), [`order-isolated.js`](k6/performance-testing/scenarios/order-isolated.js), [`catalog-isolated.js`](k6/performance-testing/scenarios/catalog-isolated.js): Ưu tiên sử dụng `user.token` có sẵn trong memory `SharedArray`, fallback gọi `/auth/login` nếu thiếu. Loại bỏ hoàn toàn chi phí BCrypt CPU ở backend.
      - [`metrics.js`](k6/performance-testing/helpers/metrics.js): Bổ sung hàm `trackErrorStatus` bóc tách lỗi chi tiết (`http_429_rate_limited`, `http_500_server_error`, `http_503_unavailable`, `http_5xx_other`) để phục vụ quan sát chính xác nguyên nhân lỗi.
    - **Verification**:
      - Chạy toàn diện `./init.sh`: Backend tests đạt **285/285 passed** (0 failures, 0 errors, 2 skipped), Frontend build hoàn tất 100%.
  - Commits: `1729f4d` - feat(perf): optimize tomcat threads, hikari pool, rate limits, and pre-signed tokens for k6 flash sale.
  - Files or artifacts updated:
    - `backend/src/main/resources/application.yml`
    - `backend/src/main/java/com/ticketbox/api/module/order/config/OrderRateLimitPolicy.java`
    - `backend/src/main/java/com/ticketbox/api/module/payment/config/PaymentRateLimitPolicy.java`
    - `k6/performance-testing/data/generate-data.js`
    - `k6/performance-testing/data/users.json`
    - `k6/performance-testing/helpers/metrics.js`
    - `k6/performance-testing/scenarios/catalog-isolated.js`
    - `k6/performance-testing/scenarios/order-isolated.js`
    - `k6/performance-testing/scenarios/flash-sale-e2e.js`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có.
  - Next best step: Khởi động backend với cấu hình mới và chạy k6 `./k6/performance-testing/run-tests.sh e2e` để đo đạc và so sánh kết quả.

