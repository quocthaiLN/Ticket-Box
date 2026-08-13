# 📅 TicketBox - Lộ Trình Triển Khai Backend Java Spring Boot Cấp Tốc (30/07/2026 – 20/08/2026)

> **Ngày cập nhật:** 04/08/2026  
> **Mục tiêu hoàn thành:** **20/08/2026 (22 Ngày Tổng / 20 Ngày Làm Việc / 2 Ngày Nghỉ Dự Phòng)**  
> **Quy tắc Chu Kỳ (Sprint Rule):**  
>   - 🗓️ **7 Ngày Làm Việc Liên Tục**: Thực hiện các nhiệm vụ từ Học -> Code -> Test -> Review.  
>   - 🏖️ **1 Ngày Nghỉ Xả Hơi / Dự Phòng (Sau Mỗi 7 Ngày làm)**: Dành 1 ngày để nghỉ ngơi tái tạo năng lượng hoặc làm "lưới bảo hiểm" xử lý trễ hạn/bug phức tạp.  
> **Mô hình làm việc:** **Human-AI Pair Programming** (🧠 Học & Kiến Trúc 40% | 🤖 AI Code 20% | 🔍 Review & Testing 40%)

---

## 📊 1. Bảng Tổng Quan Lộ Trình Cấp Tốc (30/07 – 20/08/2026)

| Chu Kỳ | Khoảng Thời Gian Làm | 🏖️ Ngày Nghỉ / Backup (1 Ngày) | Mục Tiêu Chính | ⌛ Tổng Giờ Làm | Trạng Thái |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Chu Kỳ 1** | **30/07 - 05/08/2026** | **06/08/2026** | Core Foundation, Multi-module Maven, DB Migration Flyway, Security JWT, RBAC 3 Roles & Auth Unit Testing. | **33.5h** | 🔄 Đang hoàn thiện (Ngày 7) |
| **Chu Kỳ 2** | **07/08 - 13/08/2026** | **14/08/2026** | Venue CRUD, Concert Catalog, SeatZone SVG, Redis Caching, Anti Cache-Stampede & Core Hold Ticket Engine (Pessimistic Locking + Concurrency Test). | **37.5h** | ⏳ Chưa bắt đầu |
| **Chu Kỳ 3** | **15/08 - 20/08/2026** | **20/08/2026 (Nghiệm thu)** | Order Checkout API, Tích hợp VNPay/MoMo Sandbox, Webhook Idempotency, E-Ticket QR, Async Workers (Email Thymeleaf, CSV Import, AI PDF), Bucket4j Rate Limit, k6 Load Testing & Launch. | **39.0h** | ⏳ Chưa bắt đầu |
| **TỔNG CỘNG**| **20 Ngày Làm Việc** | **2 Ngày Nghỉ Dự Phòng** | **HOÀN THÀNH TOÀN BỘ BACKEND DỰ ÁN TICKETBOX VÀO 20/08/2026** | **110.0h** | |

---

## 📆 2. Chi Tiết Nhiệm Vụ Từng Ngày (Daily Task Breakdown)

---

### 🔹 CHU KỲ 1: Infrastructure, Database Migration & Auth Module (30/07 – 06/08/2026)

#### 🗓️ Ngày 1 (30/07/2026 - Thứ Năm): Setup Project Multi-module & Docker Infrastructure
- **Task Chi Tiết**:
  1. Khởi tạo cấu hình Maven Multi-module (`backend` parent, module `api-server`, `worker-server`, `shared`).
  2. Viết file `docker-compose.yml` định nghĩa các container: PostgreSQL 16 (Port 5432), Redis 7 (Port 6379), RabbitMQ 3 Management (Port 5672/15672), Mailpit (Port 1025/8025).
  3. Cấu hình `application.yml` cơ bản kết nối PostgreSQL & Redis.
- **🧠 Học & Nắm**: Cấu trúc Maven Multi-module kế thừa POM, cơ chế networking container docker.
- **🤖 AI Code**: Khởi tạo POM file, file docker-compose.yml và file application.yml.
- **🔍 Review & Test**: Chạy `docker compose up -d`, kiểm tra log container xanh 100%, thử kết nối Database qua DBeaver.
- **⏱️ Giờ làm**: 4.5h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 2 (31/07/2026 - Thứ Sáu): Database Migration (Flyway) & JPA Entities Mapping
- **Task Chi Tiết**:
  1. Viết script Flyway Migration `V1__init_schema.sql` tạo 11 bảng chuẩn: `users`, `user_accounts`, `concerts`, `seat_zones`, `ticket_types`, `user_ticket_type_counters`, `orders`, `order_items`, `payment_transactions`, `e_tickets`, `audit_logs`, `guests`, `guest_import_jobs`, `artist_bio_jobs`, `notifications`.
  2. Tạo script `V2__seed_data.sql` khởi tạo dữ liệu mẫu Admin, Organizer, Audience & Venue mẫu.
  3. Tạo các JPA Entity Java tương ứng trong package `domain/entities`.
- **🧠 Học & Nắm**: Flyway Versioning DB, quan hệ JPA Entities (`@ManyToOne`, `@OneToMany`, `@EmbeddedId`).
- **🤖 AI Code**: Sinh các class Entity JPA & Flyway DDL script.
- **🔍 Review & Test**: Start app Spring Boot, kiểm tra bảng tự động migrate chuẩn xác trong Postgres.
- **⏱️ Giờ làm**: 5.0h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 3 (01/08/2026 - Thứ Bảy): Security Core, JWT Token Provider & Redis Denylist
- **Task Chi Tiết**:
  1. Xây dựng `JwtUtils` (generate Token, validate Token, extract Username/Roles bằng HMAC-SHA256).
  2. Xây dựng `JwtFilter` kế thừa `OncePerRequestFilter` để parse header `Authorization: Bearer <token>`.
  3. Xây dựng `TokenBlacklistService` kết nối Redis `StringRedisTemplate` để blacklist Token khi Logout (TTL bằng thời gian hết hạn JWT).
  4. Cấu hình Spring Security `SecurityConfig` (Stateless session, Disable CSRF, Bean `PasswordEncoder`).
- **🧠 Học & Nắm**: Luồng Stateless Authentication của Spring Security 6, Redis TTL Caching.
- **🤖 AI Code**: Viết `JwtUtils`, `JwtFilter`, `TokenBlacklistService` & `SecurityConfig`.
- **🔍 Review & Test**: Unit Test `JwtUtilsTest` kiểm tra sinh token và đọc payload chính xác.
- **⏱️ Giờ làm**: 6.0h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 4 (02/08/2026 - Chủ Nhật): Auth Controller APIs & Global Exception Handling
- **Task Chi Tiết**:
  1. Tạo DTOs request/response: `RegisterRequest`, `LoginRequest`, `LoginResponse`, `UserResponse`.
  2. Viết `UserService` & `UserServiceImpl` xử lý logic Đăng ký (Mã hóa mật khẩu BCrypt) & Đăng nhập (Authenticate qua AuthenticationManager).
  3. Viết `AuthController` chứa endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`.
  4. Viết `GlobalExceptionHandler` bắt ngoại lệ `AppException`, `MethodArgumentNotValidException` trả về dạng chuẩn `ApiResponse<T>`.
- **🧠 Học & Nắm**: Best practices thiết kế Restful API Response Wrapper & Centralized Exception Handling.
- **🤖 AI Code**: Sinh DTOs, Exception Handler và Controller endpoints.
- **🔍 Review & Test**: Dùng Postman test Đăng ký -> Đăng nhập nhận JWT Token thành công.
- **⏱️ Giờ làm**: 4.5h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 5 (03/08/2026 - Thứ Hai): User Me Endpoint & Phân Quyền RBAC 3 Roles
- **Task Chi Tiết**:
  1. Xây dựng endpoint `GET /api/v1/auth/users/me` lấy thông tin chi tiết user đang đăng nhập qua `SecurityContextHolder`.
  2. Xây dựng `AdminUserController` phân quyền RBAC quản lý người dùng: `GET /api/v1/admin/users`, `PUT /api/v1/admin/users/{id}/status`.
  3. Áp dụng `@PreAuthorize("hasRole('ADMIN')")` và `@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")` bảo vệ các API quản trị.
- **🧠 Học & Nắm**: Method-level security trong Spring Security, Role Hierarchy.
- **🤖 AI Code**: Viết `AdminUserController`, `AdminUserServiceImpl` & các annotation phân quyền.
- **🔍 Review & Test**: Dùng Token role `AUDIENCE` gọi route Admin -> Xác nhận hệ thống trả về `403 Forbidden` chuẩn xác.
- **⏱️ Giờ làm**: 4.0h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 6 (04/08/2026 - Thứ Ba): Auth Unit Testing & Postman Collection Setup
- **Task Chi Tiết**:
  1. Viết Unit Test `UserServiceTest` dùng Mockito (`@Mock`, `@InjectMocks`) kiểm tra đăng ký tài khoản trùng email, sai mật khẩu.
  2. Viết Integration Test `AuthControllerTest` kiểm tra HTTP status & JSON response body.
  3. Tạo Postman Collection `TicketBox_Auth_APIs.json` chứa sẵn biến môi trường (`{{jwt_token}}`, `{{base_url}}`).
- **🧠 Học & Nắm**: Mockito unit test pattern, Spring Boot `@WebMvcTest` & MockMvc.
- **🤖 AI Code**: Tạo `UserServiceTest`, `AuthControllerTest`.
- **🔍 Review & Test**: Run `./mvnw test`, xác nhận pass 100% tests Auth.
- **⏱️ Giờ làm**: 5.0h | **Trạng thái**: ✅ **Hoàn thành**

---

#### 🗓️ Ngày 7 (05/08/2026 - Thứ Tư): Code Review, Cleanup DTOs & Regression Verification Tuần 1
- **Task Chi Tiết**:
  1. Rà soát lại toàn bộ code module `auth` và `infrastructure`, chuẩn hóa đặt tên theo Java Code Convention.
  2. Kiểm tra lại việc validate dữ liệu đầu vào bằng Bean Validation (`@NotBlank`, `@Email`, `@Size`).
  3. Chạy lại toàn bộ bộ testsuite Auth và verify tính ổn định của Redis Token Blacklist.
- **🧠 Học & Nắm**: Code quality gates, Bean Validation, Clean Architecture boundary.
- **🤖 AI Code**: Tối ưu DTO annotations & import cleanup.
- **🔍 Review & Test**: Chạy thử luồng Đăng nhập -> Logout -> Dùng lại Token cũ -> Nhận `401 Unauthorized` từ Redis Blacklist.
- **⏱️ Giờ làm**: 4.5h | **Trạng thái**: 🔄 **Đang thực hiện**

---

#### 🏖️ Ngày 8 (06/08/2026 - Thứ Năm): NGHỈ XẢ HƠI / DỰ PHÒNG BÙ TIẾN ĐỘ CHU KỲ 1
- **Task Chi Tiết**: Nghỉ ngơi hoàn toàn tái tạo sức lao động HOẶC bù các bug tồn đọng của Chu kỳ 1 (nếu có).
- **⏱️ Giờ làm**: 0.0h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

### 🔹 CHU KỲ 2: Concert Catalog, Venue, Redis Caching & Ticketing Engine (07/08 – 14/08/2026)

#### 🗓️ Ngày 9 (07/08/2026 - Thứ Sáu): Venue Management & Concert CRUD APIs
- **Task Chi Tiết**:
  1. Viết `VenueController` & `VenueService`: APIs Admin/Organizer `POST /api/v1/admin/venues`, `PUT /api/v1/admin/venues/{id}`, `GET /api/v1/admin/venues`.
  2. Viết `ConcertController` & `ConcertService`: APIs Organizer `POST /api/v1/admin/concerts`, `PUT /api/v1/admin/concerts/{id}`, `DELETE /api/v1/admin/concerts/{id}`.
  3. Quản lý thông tin concert: Tên, Mô tả, Poster Banner, Thời gian bắt đầu/kết thúc, Trạng thái (`DRAFT`, `PUBLISHED`, `ENDED`).
- **🧠 Học & Nắm**: Thiết kế CRUD RESTful APIs nâng cao, quản lý trạng thái Lifecycle của Entity.
- **🤖 AI Code**: Sinh Controllers, Services & Repositories cho Venue và Concert.
- **🔍 Review & Test**: Postman test tạo Venue thành công -> Tạo Concert gán vào Venue vừa tạo.
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 10 (08/08/2026 - Thứ Bảy): SeatZone SVG Coordinates & TicketType Pricing Management
- **Task Chi Tiết**:
  1. Viết API quản lý sơ đồ khu vực ghế `SeatZoneController`: `POST/PUT /api/v1/admin/concerts/{concertId}/seat-zones` (Khu VIP, GA, Stand, tọa độ SVG X, Y, W, H, Color).
  2. Viết API quản lý Hạng vé `TicketTypeController`: `POST/PUT /api/v1/admin/concerts/{concertId}/ticket-types` (Tên loại vé, Giá tiền, Tổng số lượng `total_quantity`, Số lượng còn lại `remaining_quantity`, Giới hạn mua `max_per_user`).
- **🧠 Học & Nắm**: Cấu trúc dữ liệu Sơ đồ ghế SVG, mối quan hệ Concert -> SeatZone -> TicketType.
- **🤖 AI Code**: Sinh DTOs & Service logic thêm mới/chỉnh sửa SeatZone và TicketType.
- **🔍 Review & Test**: Postman test thêm thành công hạng vé VIP (Giá 2.000.000đ, 100 vé) gắn với SeatZone SVG Zone A.
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 11 (09/08/2026 - Chủ Nhật): Public Concert Catalog API & Redis Caching Layer
- **Task Chi Tiết**:
  1. Viết Public APIs cho khán giả không cần token: `GET /api/v1/concerts` (Hỗ trợ phân trang `Pageable`, search theo tên, filter theo ngày), `GET /api/v1/concerts/{id}` (Chi tiết concert + danh sách sơ đồ ghế & vé).
  2. Xây dựng `CatalogCacheService` dùng `@Cacheable(value = "concerts", key = "#id")` lưu kết quả chi tiết concert vào Redis dưới dạng JSON.
  3. Thêm `@CacheEviction` tự động xóa Redis cache khi Organizer cập nhật/xóa thông tin Concert.
- **🧠 Học & Nắm**: Spring Cache Abstraction kết hợp Redis, Strategy Cache-Aside Pattern.
- **🤖 AI Code**: Sinh Public Catalog Controller, Paging Specification & Redis Cache configuration.
- **🔍 Review & Test**: Dùng Postman gọi API chi tiết concert: Lần 1 (DB query ~150ms), Lần 2 (Redis cache hit < 15ms).
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 12 (10/08/2026 - Thứ Hai): Anti Cache-Stampede & Cache Invalidation Optimizations
- **Task Chi Tiết**:
  1. Áp dụng SingleFlight Pattern / Redisson Distributed Lock bảo vệ DB khỏi hiện tượng dội tải (Hotkey Expire Cache Stampede).
  2. Xử lý trường hợp khi Redis key hết hạn, chỉ duy nhất 1 thread được phép xuống DB query và ghi lại vào Redis, các threads khác phải chờ.
- **🧠 Học & Nắm**: Chống Cache Stampede / Thundering Herd Problem trong hệ thống tải cao.
- **🤖 AI Code**: Viết helper SingleFlight / Redisson Lock bọc quanh hàm get Concert Details.
- **🔍 Review & Test**: Viết test giả lập 50 request cùng lúc vào key vừa expire, verify DB chỉ nhận đúng 1 query SQL.
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 13 (11/08/2026 - Thứ Ba): Core Ticketing Engine — Hold Ticket API & Pessimistic Locking
- **Task Chi Tiết**:
  1. Tạo API `POST /api/v1/tickets/hold` truyền vào `ticketTypeId` và `quantity`.
  2. Viết `HoldTicketService`: Dùng **Pessimistic Locking** (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE` trên bảng `ticket_types`) để trừ số lượng `remaining_quantity`.
  3. Tạo bản ghi `Order` với trạng thái `HELD` và đặt `hold_expires_at = now() + 15 minutes`.
- **🧠 Học & Nắm**: Cơ chế Database Concurrency, Pessimistic Locking vs Optimistic Locking trong bài toán bán vé/giữ hàng.
- **🤖 AI Code**: Viết Repository query custom with `@Lock` & `HoldTicketService`.
- **🔍 Review & Test**: Test giữ vé thành công: Số lượng vé giảm trong DB, Order được khởi tạo trạng thái `HELD`.
- **⏱️ Giờ làm**: 6.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 14 (12/08/2026 - Thứ Tư): Per-User Ticket Limit & Hold Expiry Scheduler
- **Task Chi Tiết**:
  1. Kiểm tra giới hạn số vé mỗi người dùng mua (`max_per_user`): Kết hợp Redis Atomic Counter (`INCRBY`) và lưu persistent bảng `user_ticket_type_counters`. Chặn ngay nếu vượt quá giới hạn.
  2. Xây dựng `@Scheduled(fixedDelay = 30000)` Scheduler trong `api-server` tự động quét các Order `HELD` đã quá `hold_expires_at`: Trả lại số lượng `remaining_quantity` vào `ticket_types` và chuyển Order sang `EXPIRED`.
- **🧠 Học & Nắm**: Atomic operations trong Redis, Spring `@Scheduled` background tasks & Transactional rollback logic.
- **🤖 AI Code**: Viết UserTicketLimitValidator & HoldExpiryScheduler.
- **🔍 Review & Test**: Sửa tạm expiry time = 10 giây, verify sau 10s Scheduler tự động nhả vé bị giam về kho.
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 15 (13/08/2026 - Thứ Năm): Hold Ticket Concurrency Testing & Anti-Oversell Verification
- **Task Chi Tiết**:
  1. Viết test case `HoldTicketConcurrencyTest` sử dụng `ExecutorService` & `CountDownLatch` giả lập **100 threads đồng thời** tranh mua 10 vé cuối cùng của 1 TicketType.
  2. Đảm bảo đúng 10 threads giữ vé thành công, 90 threads nhận thông báo "Vé đã hết" hoặc "Không đủ số lượng", tuyệt đối **không bị oversell** (`remaining_quantity < 0`).
- **🧠 Học & Nắm**: Testing Concurrency trong Java/Spring Boot với `CountDownLatch`, `CompletableFuture`.
- **🤖 AI Code**: Tạo class test `HoldTicketConcurrencyTest`.
- **🔍 Review & Test**: Run test concurrency 5 lần liên tiếp: Tất cả đều pass 100%, 0% sai lệch dữ liệu.
- **⏱️ Giờ làm**: 4.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🏖️ Ngày 16 (14/08/2026 - Thứ Sáu): NGHỈ XẢ HƠI / DỰ PHÒNG BÙ TIẾN ĐỘ CHU KỲ 2
- **Task Chi Tiết**: Nghỉ ngơi hoàn toàn tái tạo sức lao động HOẶC bù các bug tồn đọng của Chu kỳ 2 (nếu có).
- **⏱️ Giờ làm**: 0.0h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

### 🔹 CHU KỲ 3: Payment Gateways, Async Workers, Anti-Bot & Final Launch (15/08 – 20/08/2026)

#### 🗓️ Ngày 17 (15/08/2026 - Thứ Bảy): Order Checkout API & VNPay/MoMo Payment Gateways Integration
- **Task Chi Tiết**:
  1. Viết API `POST /api/v1/orders/{id}/checkout`: Kiểm tra Order HELD còn thời hạn -> Đổi trạng thái sang `PENDING_PAYMENT`.
  2. Viết `VNPayService`: Tạo mã HmacSHA512 checksum, sinh URL chuyển hướng sang cổng thanh toán VNPay Sandbox.
  3. Viết `MoMoService`: Xây dựng HMAC Signature, tạo request thanh toán QR Code / Payment Link MoMo Sandbox.
- **🧠 Học & Nắm**: Quy trình tích hợp cổng thanh toán trực tuyến, thuật toán HmacSHA512 Security Checksum.
- **🤖 AI Code**: Sinh `VNPayService`, `MoMoService` và Checkout Controller.
- **🔍 Review & Test**: Dùng Postman gọi Checkout -> Trả về URL VNPay/MoMo Sandbox chuẩn, click mở ra đúng trang thanh toán giả lập.
- **⏱️ Giờ làm**: 6.0h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 18 (16/08/2026 - Chủ Nhật): Payment Webhook Handlers, Idempotency & Order Finalization
- **Task Chi Tiết**:
  1. Viết Callback Endpoints: `GET /api/v1/payments/vnpay-callback` & `POST /api/v1/payments/momo-callback`.
  2. Validate Checksum hash từ VNPay/MoMo gửi về để chống giả mạo request thanh toán.
  3. Xử lý **Payment Idempotency (Anti-double-spending)**: Ghi log vào bảng `payment_transactions` theo `transaction_id` duy nhất; nếu đã xử lý rồi thì bỏ qua.
  4. Đổi status Order sang `PAID`, tự động sinh mã vé vào bảng `e_tickets`, bắn sự kiện `OrderPaidEvent` vào RabbitMQ Queue (`ticketbox.order.paid`).
- **🧠 Học & Nắm**: Webhook Security Validation, Idempotency Key Pattern & Messaging Event Publishing.
- **🤖 AI Code**: Viết Callback Handlers, Idempotency Service & RabbitMQ Event Publisher.
- **🔍 Review & Test**: Giả lập webhook call 2 lần trùng `transaction_id` -> Xác nhận hệ thống chỉ xử lý 1 lần duy nhất, Order đổi `PAID`.
- **⏱️ Giờ làm**: 6.0h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 19 (17/08/2026 - Thứ Hai): E-Ticket QR Code Generator & Async Worker Services
- **Task Chi Tiết**:
  1. Xây dựng `ETicketService` dùng thư viện ZXing sinh mã QR Code HMAC-SHA256 bảo mật chứa mã vé. API `GET /api/v1/tickets/me` xem vé đã mua.
  2. Setup module `worker-server` độc lập kết nối RabbitMQ listener (`@RabbitListener`):
     - **Email Worker**: Nghe `OrderPaidEvent` -> Render template HTML Thymeleaf -> Gửi qua `JavaMailSender` (đến Mailpit).
     - **CSV Import Worker**: API Upload CSV danh sách khách VIP (`/admin/guests/import`) -> Đẩy job sang Worker parse OpenCSV & Batch Insert DB.
     - **AI Artist Bio Worker**: API Upload PDF Presskit -> Worker dùng Apache PDFBox extract text -> Gọi Gemini AI API tóm tắt tiểu sử nghệ sĩ -> Update DB.
- **🧠 Học & Nắm**: Event-Driven Async Architecture với RabbitMQ, ZXing QR, OpenCSV Batching, Gemini API integration.
- **🤖 AI Code**: Viết `ETicketService`, `worker-server` listeners (Email, CSV Batch, AI Gemini API).
- **🔍 Review & Test**: Đặt vé thành công -> Mở Mailpit Web UI (Port 8025) kiểm tra nhận Email HTML đẹp mắt kèm mã QR vé E-ticket.
- **⏱️ Giờ làm**: 6.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 20 (18/08/2026 - Thứ Ba): Anti-Bot Rate Limiting (Bucket4j) & Audit Logging (Spring AOP)
- **Task Chi Tiết**:
  1. Tích hợp Bucket4j vào Spring Security Filter: Giới hạn IP / User khi gọi API Hold vé (Tối đa 5 requests/phút). Trả về HTTP `429 Too Many Requests` khi quá ngưỡng.
  2. Xây dựng Custom Annotation `@AuditLog` và Spring AOP Aspect (`AuditLogAspect`): Tự động bắt các thao tác nhạy cảm của Admin/Organizer (Tạo/Sửa Concert, Thay đổi trạng thái User) và ghi lịch sử vào bảng `audit_logs`.
- **🧠 Học & Nắm**: Token Bucket Algorithm trong Rate Limiting (Bucket4j), Spring AOP Aspect Oriented Programming (`@Around`, `@AfterReturning`).
- **🤖 AI Code**: Viết `RateLimitFilter` (Bucket4j) và `AuditLogAspect`.
- **🔍 Review & Test**: Dùng Postman loop 6 lần API Hold vé trong 10 giây -> Request thứ 6 nhận response 429. Kiểm tra DB `audit_logs` có bản ghi thao tác admin.
- **⏱️ Giờ làm**: 5.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 21 (19/08/2026 - Thứ Tư): Load Testing (k6), Frontend Web Connection & Bug Fixing
- **Task Chi Tiết**:
  1. Viết k6 Load Test script mô phỏng **1,000 Virtual Users (VUs)** tấn công đồng thời vào luồng Xem Concert -> Hold Vé. Kiểm tra chỉ số P95 Response Time < 200ms.
  2. Ghép nối Frontend Web App (React/Next.js có sẵn) với Backend Spring Boot: Cấu hình CORS (`CorsConfigurationSource`), fix triệt để các lỗi kết nối cross-origin.
  3. Rà soát fix toàn bộ các lỗi nhỏ còn tồn đọng trong quá trình End-to-End Test.
- **🧠 Học & Nắm**: Performance Testing với k6, CORS headers, End-to-End Integration Debugging.
- **🤖 AI Code**: Sinh script k6 test `hold_ticket_load_test.js` & Cấu hình CORS.
- **🔍 Review & Test**: Chạy k6 test report xanh 100%, thao tác mua vé thử trực tiếp trên giao diện Frontend Web mượt mà.
- **⏱️ Giờ làm**: 6.5h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

#### 🗓️ Ngày 22 (20/08/2026 - Thứ Năm): OpenAPI Swagger Documentation & Nghiệm Thu Bàn Giao
- **Task Chi Tiết**:
  1. Tích hợp `springdoc-openapi-starter-webmvc-ui`: Tự động sinh giao diện Swagger UI tại `/swagger-ui.html`.
  2. Thêm OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Schema`) mô tả chi tiết từng API endpoint, DTOs & Authentication Bearer Token.
  3. Thực hiện Sanity Check toàn bộ luồng hệ thống lần cuối.
  4. **NGHIỆM THU & BÀN GIAO DỰ ÁN 100% SUCCESSFUL!** 🎉
- **🧠 Học & Nắm**: OpenAPI 3.0 specification standards & Documentation best practices.
- **🤖 AI Code**: Thêm Swagger Annotations vào toàn bộ Controllers & DTOs.
- **🔍 Review & Test**: Truy cập `http://localhost:8080/swagger-ui.html`, test trực tiếp các API trên Swagger UI thành công.
- **⏱️ Giờ làm**: 4.0h | **Trạng thái**: ⏳ **Chưa bắt đầu**

---

## 🌟 Cam Kết Đảm Bảo Tiến Độ & Chất Lượng (Quality & Delivery Gate):
1. **Không Bỏ Rơi Chất Lượng Code**: Dù tiến độ nén gấp rút đến ngày 20/08/2026, từng ngày đều có bước **Review & Test** khép kín để tránh đọng nợ kỹ thuật (Technical Debt).
2. **Buffer An Toàn Chống Kiệt Sức**: Cứ sau 7 ngày cày ải liên tục, bạn được giữ lại **1 ngày nghỉ trọn vẹn (06/08 và 14/08)** để nạp lại năng lượng hoặc đóng vai trò "lưới bảo vệ" nếu gặp bug cực khó.
3. **Theo Sát Mã Nguồn backend Thực Tế**: Toàn bộ từ Ngày 1 đến Ngày 6 (30/07 – 04/08/2026) đã được đối chiếu trực tiếp với mã nguồn hiện tại và đánh dấu chính xác là `✅ Hoàn thành`.
