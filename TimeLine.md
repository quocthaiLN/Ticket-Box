# 📅 TicketBox - Lộ Trình Triển Khai Backend Java Spring Boot Chi Tiết (7 Ngày Làm + 2 Ngày Nghỉ)

> **Ngày cập nhật:** 29/07/2026  
> **Thời gian dự án:** 30/07/2026 – 21/09/2026 (6 Sprint / 54 Ngày Tổng)  
> **Mô hình Chu kỳ (Sprint Cycle):**  
>   - 🗓️ **7 Ngày Làm Việc & Học Tập Liên Tục**: Hoàn thiện toàn bộ các task nhỏ từ Học -> Code AI -> Review & Test.  
>   - 🏖️ **2 Ngày Nghỉ / Dự Phòng Sau Mỗi Tuần**: Dành trọn vẹn 2 ngày để nghỉ ngơi xả hơi, nạp lại năng lượng hoặc bù tiến độ nếu có việc bận phát sinh!  
> **Mô hình làm việc:** **Human-AI Pair Programming** (🧠 Học 48% | 🤖 AI Code 12% | 🔍 Review 40%)

---

## 📊 1. Bảng Tổng Quan 6 Chu Kỳ (7 Ngày Làm + 2 Ngày Nghỉ)

| Chu Kỳ / Tuần | Khoảng Thời Gian Làm (7 Ngày) | 🏖️ Ngày Nghỉ / Backup (2 Ngày) | Mục Tiêu Chính | ⌛ Tổng Giờ Làm | Trạng Thái |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Tuần 1** | **30/07 - 05/08/2026** | **06/08 - 07/08/2026** | Core Foundation, Multi-module, DB Migration & Auth RBAC | **32.5h** | 🔄 Đang thực hiện |
| **Tuần 2** | **08/08 - 14/08/2026** | **15/08 - 16/08/2026** | Concert Catalog, Venue, Seat Zone SVG & Redis Cache | **33.5h** | ⏳ Chưa bắt đầu |
| **Tuần 3** | **17/08 - 23/08/2026** | **24/08 - 25/08/2026** | Ticketing Engine (Hold Tickets, Pessimistic Lock, Per-user limit) | **35.5h** | ⏳ Chưa bắt đầu |
| **Tuần 4** | **26/08 - 01/09/2026** | **02/09 - 03/09/2026** | Order Checkout, Tích hợp VNPay/MoMo Sandbox & Payment Webhook | **35.0h** | ⏳ Chưa bắt đầu |
| **Tuần 5** | **04/09 - 10/09/2026** | **11/09 - 12/09/2026** | E-Ticket QR, Async Workers (CSV Guest List, AI Bio PDF, Email) | **34.0h** | ⏳ Chưa bắt đầu |
| **Tuần 6** | **13/09 - 19/09/2026** | **20/09 - 21/09/2026** | Bucket4j Rate Limit, Audit Log, Load Testing (k6) & Ghép FE Web | **37.5h** | ⏳ Chưa bắt đầu |
| **TỔNG CỘNG**| **42 Ngày Làm Việc** | **12 Ngày Nghỉ Dự Phòng** | **HOÀN THÀNH TOÀN BỘ BACKEND DỰ ÁN TICKETBOX** | **208.0h** | |

---

## 📆 2. Kế Hoạch Chi Tiết Theo Ngày & Chu Kỳ Nghỉ

### 🔹 TUẦN 1: Infrastructure, Database Migration & Auth Module (30/07 – 07/08/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **30/07 (T5)** | 🗓️ Ngày 1 | **Setup Project & Docker**: Multi-module (`api-server`, `worker-server`, `shared`), `docker-compose.yml` (Postgres, Redis, RabbitMQ, Mailpit). | **2.5h** | **0.5h** | **1.5h** | **4.5h** | Multi-module Maven runnable, Postgres, Redis, RabbitMQ, Mailpit container up. | 🔄 Đang thực hiện |
| **31/07 (T6)** | 🗓️ Ngày 2 | **DB Migration**: Flyway script `V1__init_schema.sql` (11 bảng) + JPA Entities mapping & Repositories. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | 11 Bảng PostgreSQL khởi tạo chuẩn qua Flyway, App auto-migrate thành công. | ⏳ Chưa bắt đầu |
| **01/08 (T7)** | 🗓️ Ngày 3 | **Security Core**: `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`, Redis Denylist Service. | **3.0h** | **1.0h** | **2.0h** | **6.0h** | Component JWT Security & Redis Denylist hoạt động độc lập. | ⏳ Chưa bắt đầu |
| **02/08 (CN)** | 🗓️ Ngày 4 | **Auth APIs**: Endpoints `/register`, `/login`, `/refresh-token`, `/logout`, DTOs & Global Exception Handler. | **2.0h** | **0.5h** | **2.0h** | **4.5h** | Postman test Đăng ký & Đăng nhập thành công, nhận Access Token. | ⏳ Chưa bắt đầu |
| **03/08 (T2)** | 🗓️ Ngày 5 | **User & RBAC**: Endpoint `/users/me`, phân quyền RBAC 3 Roles (`ADMIN`, `ORGANIZER`, `AUDIENCE`) qua `@PreAuthorize`. | **2.0h** | **0.5h** | **1.5h** | **4.0h** | User role AUDIENCE bị chặn 403 route Admin/Organizer. | ⏳ Chưa bắt đầu |
| **04/08 (T3)** | 🗓️ Ngày 6 | **Testing & Postman**: Viết Unit Test `AuthServiceTest` & Postman collection mẫu cho Auth Module. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Unit Test Auth Coverage > 80%, Postman collection xanh hết. | ⏳ Chưa bắt đầu |
| **05/08 (T4)** | 🗓️ Ngày 7 | **Review Tuần 1**: Code Review cá nhân, dọn dẹp DTOs, verify full regression test Tuần 1. | **1.5h** | **0.5h** | **1.5h** | **3.5h** | Clean Code 100%, sẵn sàng bàn giao sản phẩm Tuần 1. | ⏳ Chưa bắt đầu |
| **06/08 (T5)** | 🏖️ **NGHỈ 1** | **Nghỉ Xả Hơi / Dự Phòng**: Xả hơi nạp lại năng lượng hoặc bù tiến độ nếu có việc bận đột xuất. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |
| **07/08 (T6)** | 🏖️ **NGHỈ 2** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn, chuẩn bị tinh thần cho Tuần 2. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |

---

### 🔹 TUẦN 2: Concert Catalog, Venue & Redis Caching (08/08 – 16/08/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **08/08 (T7)** | 🗓️ Ngày 1 | **Venue Management**: APIs Admin/Organizer: `POST/PUT/GET /api/v1/admin/venues`. Quản lý địa điểm, sức chứa. | **2.0h** | **0.5h** | **1.5h** | **4.0h** | CRUD Venue thành công, lưu DB chính xác. | ⏳ Chưa bắt đầu |
| **09/08 (CN)** | 🗓️ Ngày 2 | **Concert CRUD**: APIs Organizer: `POST/PUT/DELETE /api/v1/admin/concerts`. Quản lý concert, banner, mở bán. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Organizer tạo & chỉnh sửa concert mượt mà. | ⏳ Chưa bắt đầu |
| **10/08 (T2)** | 🗓️ Ngày 3 | **SeatZone & TicketType**: APIs khu vực ghế SVG (`/seat-zones`) & loại vé (`ticket_types`: giá, số lượng, max_per_user). | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Thêm thành công VIP, GA kèm vị trí hiển thị SVG. | ⏳ Chưa bắt đầu |
| **11/08 (T3)** | 🗓️ Ngày 4 | **Public Catalog API**: Public APIs: `GET /api/v1/concerts` (Paging/Filter), `GET /api/v1/concerts/{id}` (Chi tiết concert). | **2.0h** | **0.5h** | **2.0h** | **4.5h** | Khán giả xem được danh sách & chi tiết concert. | ⏳ Chưa bắt đầu |
| **12/08 (T4)** | 🗓️ Ngày 5 | **Redis Cache Layer**: `CatalogCacheService`: Cache danh sách & chi tiết concert vào Redis. Cache Eviction khi update. | **3.0h** | **0.5h** | **2.0h** | **5.5h** | Response chi tiết concert < 20ms từ Redis. | ⏳ Chưa bắt đầu |
| **13/08 (T5)** | 🗓️ Ngày 6 | **Anti Cache-Stampede**: Áp dụng SingleFlight Pattern / Redisson Lock cho CacheMiss để ngăn dội tải DB khi key expire. | **3.0h** | **0.5h** | **2.0h** | **5.5h** | DB không bị sập khi Redis cache hết hạn. | ⏳ Chưa bắt đầu |
| **14/08 (T6)** | 🗓️ Ngày 7 | **Catalog Testing**: Integration Test cho Catalog APIs + Redis Caching. Đo latency response & test invalidation. | **1.5h** | **0.5h** | **2.0h** | **4.0h** | Tuần 2 hoàn thành 100%. | ⏳ Chưa bắt đầu |
| **15/08 (T7)** | 🏖️ **NGHỈ 1** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoặc bù tiến độ Tuần 2. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |
| **16/08 (CN)** | 🏖️ **NGHỈ 2** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn, chuẩn bị cho Tuần 3. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |

---

### 🔹 TUẦN 3: Core Ticketing Engine — Hold Vé & Anti-Oversell (17/08 – 25/08/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **17/08 (T2)** | 🗓️ Ngày 1 | **Order Domain Model**: Domain `OrderEntity`, `OrderItemEntity`, trạng thái (`HELD`, `PENDING_PAYMENT`, `PAID`, `EXPIRED`). | **2.0h** | **0.5h** | **1.5h** | **4.0h** | Order domain skeleton hoàn thiện. | ⏳ Chưa bắt đầu |
| **18/08 (T3)** | 🗓️ Ngày 2 | **Hold Ticket Engine**: API `POST /api/v1/tickets/hold`: Dùng **Pessimistic Locking** (`SELECT ... FOR UPDATE`) trên `ticket_types`. | **3.5h** | **1.0h** | **2.5h** | **7.0h** | Giữ vé thành công, tạo Order HELD `hold_expires_at`. | ⏳ Chưa bắt đầu |
| **19/08 (T4)** | 🗓️ Ngày 3 | **Per-User Ticket Limit**: Kiểm tra số vé mua: Kết hợp Redis Atomic Counter (`INCRBY`) và `UserTicketTypeCounterEntity` DB. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Chặn 1 user mua quá max_per_user thành công. | ⏳ Chưa bắt đầu |
| **20/08 (T5)** | 🗓️ Ngày 4 | **Hold Expiry Scheduler**: `@Scheduled(fixedDelay = 30000)` trong `api-server`: Quét đơn HELD hết hạn -> Trả vé -> Đổi status `EXPIRED`. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Tự động nhả vé bị giam sau 15 phút không trả tiền. | ⏳ Chưa bắt đầu |
| **21/08 (T6)** | 🗓️ Ngày 5 | **View Pending Orders**: API `GET /api/v1/orders/{id}`: Xem chi tiết giỏ hàng/vé đang giữ và đếm ngược thời gian hết hạn. | **1.5h** | **0.5h** | **1.5h** | **3.5h** | API trả về thông tin giỏ hàng & countdown chuẩn. | ⏳ Chưa bắt đầu |
| **22/08 (T7)** | 🗓️ Ngày 6 | **Concurrency Test**: `HoldTicketConcurrencyTest` dùng `ExecutorService` & `CountDownLatch` giả lập 100 threads cố mua 10 vé cuối. | **3.0h** | **1.0h** | **2.5h** | **6.5h** | Khẳng định bán đúng 10 vé, 90 thread còn lại báo hết. | ⏳ Chưa bắt đầu |
| **23/08 (CN)** | 🗓️ Ngày 7 | **Optimizing Locks**: Thu hẹp Transaction Scope, review code & chốt Tuần 3. | **2.0h** | **0.5h** | **2.0h** | **4.5h** | Tuần 3 hoàn thành 100%. | ⏳ Chưa bắt đầu |
| **24/08 (T2)** | 🏖️ **NGHỈ 1** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoặc bù tiến độ Tuần 3. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |
| **25/08 (T3)** | 🏖️ **NGHỈ 2** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn, chuẩn bị cho Tuần 4. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |

---

### 🔹 TUẦN 4: Order Checkout, Payment Gateways & Webhooks (26/08 – 03/09/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **26/08 (T4)** | 🗓️ Ngày 1 | **Order Checkout API**: API `POST /api/v1/orders/{id}/checkout`: Validate Order HELD còn hiệu lực -> Chuyển `PENDING_PAYMENT`. | **2.0h** | **0.5h** | **1.5h** | **4.0h** | API sẵn sàng sinh URL thanh toán. | ⏳ Chưa bắt đầu |
| **27/08 (T5)** | 🗓️ Ngày 2 | **Tích hợp VNPay**: Viết `VNPayService`: Tạo checksum HmacSHA512, build Payment URL VNPay Sandbox. | **3.0h** | **1.0h** | **2.5h** | **6.5h** | Redirect thành công sang trang VNPay Sandbox. | ⏳ Chưa bắt đầu |
| **28/08 (T6)** | 🗓️ Ngày 3 | **Tích hợp MoMo**: Viết `MoMoService`: Xây dựng HMAC Signature, gọi API tạo giao dịch MoMo Payment Gateway. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Sinh thành công MoMo Payment Link / QR. | ⏳ Chưa bắt đầu |
| **29/08 (T7)** | 🗓️ Ngày 4 | **Payment Webhook**: Endpoints Callback `/vnpay-callback` & `/momo-callback`. Validate checksum & Digest. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Tiếp nhận callback & xác minh chữ ký chuẩn. | ⏳ Chưa bắt đầu |
| **30/08 (CN)** | 🗓️ Ngày 5 | **Payment Idempotency**: Anti-double-spending: Lưu kết quả vào bảng `payments` dùng `transaction_id` / `vnp_TxnRef`. | **3.0h** | **0.5h** | **2.0h** | **5.5h** | Chống trừ tiền trùng lặp & xác nhận vé 2 lần 100%. | ⏳ Chưa bắt đầu |
| **31/08 (T2)** | 🗓️ Ngày 6 | **Order Finalization**: Chuyển Order `PAID`, sinh vé `e_tickets`, bắn `OrderPaidEvent` vào RabbitMQ Queue. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Order chốt PAID, event đẩy vào RabbitMQ queue. | ⏳ Chưa bắt đầu |
| **01/09 (T3)** | 🗓️ Ngày 7 | **Payment E2E Test**: Integration test toàn bộ luồng Giữ vé -> Checkout -> Callback Mock -> Order PAID. | **1.5h** | **0.5h** | **2.0h** | **4.0h** | Tuần 4 hoàn thành 100%. | ⏳ Chưa bắt đầu |
| **02/09 (T4)** | 🏖️ **NGHỈ 1** | **Nghỉ Lễ 2/9 / Dự Phòng**: Nghỉ lễ Quốc Khánh 2/9 thư giãn nạp năng lượng! | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |
| **03/09 (T5)** | 🏖️ **NGHỈ 2** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn, chuẩn bị cho Tuần 5. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |

---

### 🔹 TUẦN 5: E-Ticket QR, Async Workers & Email Notifications (04/09 – 12/09/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **04/09 (T6)** | 🗓️ Ngày 1 | **E-Ticket QR Generator**: `ETicketService`: Sinh mã QR HMAC-SHA256 (ZXing). API `/api/v1/tickets/me` xem vé đã mua. | **2.0h** | **0.5h** | **2.0h** | **4.5h** | Khán giả xem được danh sách vé kèm mã QR. | ⏳ Chưa bắt đầu |
| **05/09 (T7)** | 🗓️ Ngày 2 | **`worker-server` Setup**: Cấu hình module `worker-server` (Stateless App). Cấu hình RabbitMQ `@RabbitListener`. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Worker listener kết nối RabbitMQ thành công. | ⏳ Chưa bắt đầu |
| **06/09 (CN)** | 🗓️ Ngày 3 | **Worker: VIP Guest CSV**: API upload CSV ở `api-server` -> Đẩy job sang `worker-server` -> Parse OpenCSV -> Batch insert. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Import 1,000 khách mời VIP từ CSV mượt mà. | ⏳ Chưa bắt đầu |
| **07/09 (T2)** | 🗓️ Ngày 4 | **Worker: AI Artist Bio**: API upload PDF Presskit -> `worker-server` extract text bằng PDFBox -> Gọi Gemini API -> Update DB. | **3.0h** | **1.0h** | **2.5h** | **6.5h** | AI tự động sinh tóm tắt nghệ sĩ lên chi tiết concert. | ⏳ Chưa bắt đầu |
| **08/09 (T3)** | 🗓️ Ngày 5 | **Notification Worker**: `worker-server` nghe `OrderPaidEvent` -> Render HTML Thymeleaf -> Gửi qua `JavaMailSender`. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | Khán giả nhận Email E-ticket HTML kèm mã QR. | ⏳ Chưa bắt đầu |
| **09/09 (T4)** | 🗓️ Ngày 6 | **Async Pipeline Test**: Test toàn bộ chuỗi xử lý ngầm (RabbitMQ Queue, Worker CSV, AI PDF & Email). | **1.5h** | **0.5h** | **2.5h** | **4.5h** | Mọi tác vụ I/O nặng đều chạy ngầm mượt mà. | ⏳ Chưa bắt đầu |
| **10/09 (T5)** | 🗓️ Ngày 7 | **Review Worker Module**: Refactor code worker, tối ưu retry policy với `@Retryable` cho REST internal calls. | **1.5h** | **0.5h** | **1.5h** | **3.5h** | Tuần 5 hoàn thành 100%. | ⏳ Chưa bắt đầu |
| **11/09 (T6)** | 🏖️ **NGHỈ 1** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoặc bù tiến độ Tuần 5. | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |
| **12/09 (T7)** | 🏖️ **NGHỈ 2** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn, chuẩn bị cho Tuần 6 (Tuần Cuối). | **-** | **-** | **-** | **0.0h** | Thư giãn / Bù trễ tiến độ nếu có. | ⏳ Chưa bắt đầu |

---

### 🔹 TUẦN 6: Anti-Bot, Rate Limiting, Audit Logging & Launch Prep (13/09 – 21/09/2026)

| Ngày | Loại Ngày | Task / Nhiệm Vụ | 🧠 Học & Nắm | 🤖 AI Code | 🔍 Review & Test | ⌛ Tổng | Đầu Ra Mong Đợi (Deliverables) | Trạng Thái |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **13/09 (CN)** | 🗓️ Ngày 1 | **Bucket4j Rate Limit**: Bucket4j Filter vào Spring Security: Giới hạn req per-IP & per-User cho API Hold Vé (max 5 req/p). | **3.0h** | **0.5h** | **2.0h** | **5.5h** | Trả về `429 Too Many Requests` khi spam request. | ⏳ Chưa bắt đầu |
| **14/09 (T2)** | 🗓️ Ngày 2 | **Audit Logging (AOP)**: Custom `@AuditLog`. Spring AOP Aspect tự động ghi vết hành động quản trị vào `audit_logs`. | **2.5h** | **0.5h** | **2.0h** | **5.0h** | ADMIN xem được toàn bộ lịch sử thao tác Organizer. | ⏳ Chưa bắt đầu |
| **15/09 (T3)** | 🗓️ Ngày 3 | **Load Test (k6)**: Viết k6 script mô phỏng 1,000 VUs đồng thời tấn công vào API Hold vé của 1 TicketType có 50 vé. | **3.0h** | **1.0h** | **2.5h** | **6.5h** | Kết quả: 0% oversell, Redis/DB chịu tải tốt. | ⏳ Chưa bắt đầu |
| **16/09 (T4)** | 🗓️ Ngày 4 | **Frontend Web Connect**: Kết nối Frontend Web App (React/Next.js có sẵn) với Backend Spring Boot. Fix lỗi CORS. | **2.0h** | **0.5h** | **2.5h** | **5.0h** | Khán giả thao tác trực tiếp trên FE Web mua vé. | ⏳ Chưa bắt đầu |
| **17/09 (T5)** | 🗓️ Ngày 5 | **Sanity & Bug Fix**: Rà soát toàn bộ luồng End-to-End từ Đăng ký -> Xem Concert -> Đặt vé -> Thanh toán -> Nhận Email. | **1.5h** | **0.5h** | **2.5h** | **4.5h** | Sửa tất cả các lỗi phát sinh còn tồn đọng. | ⏳ Chưa bắt đầu |
| **18/09 (T6)** | 🗓️ Ngày 6 | **OpenAPI & Swagger**: Tích hợp `springdoc-openapi` tự động sinh Swagger UI. Export Postman OpenAPI spec. | **1.5h** | **0.5h** | **1.5h** | **3.5h** | Document API Swagger chuyên nghiệp. | ⏳ Chưa bắt đầu |
| **19/09 (T7)** | 🗓️ Ngày 7 | **Demo Preparation**: Chuẩn bị kịch bản Demo, slide thuyết minh kiến trúc Event-Driven Modular Monolith. | **3.0h** | **0.5h** | **1.5h** | **5.0h** | Đã sẵn sàng cho buổi báo cáo / nghiệm thu. | ⏳ Chưa bắt đầu |
| **20/09 (CN)** | 🏖️ **NGHỈ 1** | **Nghỉ Xả Hơi / Dự Phòng**: Thư giãn hoàn toàn trước ngày bảo vệ. | **-** | **-** | **-** | **0.0h** | Báo cáo thử / Nghỉ ngơi. | ⏳ Chưa bắt đầu |
| **21/09 (T2)** | 🏖️ **BÀN GIAO** | **Nghiệm Thu Dự Án**: **HOÀN THÀNH DỰ ÁN 100%!** | **1.0h** | **0.5h** | **1.5h** | **3.0h** | Backend Spring Boot chạy hoàn hảo. | ⏳ Chưa bắt đầu |

---

### 🌟 Ưu Điểm Tuyệt Vời Của Mô Hình Mới Này:
1. **Giữ trọn vẹn 100% Nội dung Lộ trình cũ**: Cả 7 ngày làm việc đều được giữ nguyên với đúng 208 giờ học & review.
2. **Không lo kiệt sức (Burnout-free)**: Cứ xong 1 tuần học tập & làm việc chăm chỉ, bạn được **thưởng trọn 2 ngày nghỉ ngơi** để xả hơi.
3. **Cực kỳ an toàn (Safety Buffer)**: Nếu 7 ngày làm việc có lỡ bị nghẽn 1 bug khó, 2 ngày nghỉ liền kề sẽ lập tức đóng vai trò là "lưới bảo hiểm" giúp bạn xử lý triệt để bug mà không bị trượt mốc bắt đầu của Tuần tiếp theo.
