# TicketBox - Project Structure

Dự án TicketBox được thiết kế theo kiến trúc **Modular Monolith** (Single JAR deployment, Bounded Contexts tách biệt).

---

## 1. Project High Level Structure

Cấu trúc tổng quan ở mức high-level của toàn bộ repository:

```text
Ticket-Box/
├── backend/                                         # Spring Boot Modular Monolith (Java 21, Spring Boot 3)
│   ├── pom.xml                                      # Maven root cấu hình dependencies, plugins
│   ├── mvnw / mvnw.cmd                              # Maven wrapper scripts
│   └── src/
│       ├── main/
│       │   ├── java/com/ticketbox/api/
│       │   │   ├── TicketboxApiApplication.java     # Spring Boot Main Application Class
│       │   │   │
│       │   │   ├── infrastructure/                  # Nền tảng hạ tầng kỹ thuật & cross-cutting concerns
│       │   │   │
│       │   │   ├── module/                          # Các Bounded Context Modules độc lập
│       │   │   │   ├── artistbio/                   # Tạo tiểu sử nghệ sĩ bằng AI & xử lý trích xuất PDF
│       │   │   │   ├── audit/                       # Ghi nhận và lưu vết audit log hệ thống
│       │   │   │   ├── auth/                        # Xác thực, phân quyền RBAC, OAuth2/OIDC, OTP
│       │   │   │   ├── catalog/                     # Quản lý sự kiện, concert, sơ đồ khu vực & loại vé
│       │   │   │   ├── guestlist/                   # Danh sách khách mời & import dữ liệu
│       │   │   │   ├── notification/                # Thông báo và gửi email bất đồng bộ qua RabbitMQ
│       │   │   │   ├── order/                       # Đặt chỗ, giữ vé, quản lý đơn hàng & hết hạn đơn
│       │   │   │   ├── payment/                     # Tích hợp cổng thanh toán (VNPay, MoMo), đối soát & retry
│       │   │   │   ├── shared/                      # Tiện ích dùng chung (Cache, Idempotency, Storage,...)
│       │   │   │   └── ticket/                      # Quản lý vé điện tử & tạo mã HMAC QR code
│       │   │   │
│       │   │   └── worker/                          # Background Worker process (@Profile("worker"))
│       │   │
│       │   └── resources/                           # Cấu hình application*.yml, db migrations, rate-limit lua
│       └── test/                                    # Unit & Integration tests
│
├── frontend/                                        # Giao diện web người dùng & Admin Dashboard (React, Vite, TS)
│   ├── package.json                                 # Cấu hình dependencies, build script
│   ├── vite.config.ts / vitest.config.ts            # Cấu hình Vite & Vitest
│   └── src/                                         # Mã nguồn frontend (pages, components, api client, hooks)
│
├── blueprint/                                       # Tài liệu đặc tả kỹ thuật, thiết kế DB, API design & system flows
├── nginx/                                           # Cấu hình Reverse Proxy & Load Balancer (nginx.conf)
├── postman/                                         # Bộ API Collections & Environment test cho Postman
├── docker-compose.yml                               # Khởi tạo Postgres 15, Redis 7, RabbitMQ 3.12, MinIO
└── init.sh                                          # Script khởi tạo ban đầu cho dự án
```

---

## 2. Infrastructure Folder

Thư mục `backend/src/main/java/com/ticketbox/api/infrastructure/` quản lý các cấu hình nền tảng, cross-cutting concerns và tích hợp kỹ thuật dùng chung cho toàn hệ thống. Được tổ chức thành các nhóm chức năng cụ thể:

* **`config/` (System Configurations):**
  * `SecurityConfig.java`: Cấu hình Spring Security, phân quyền endpoint, stateless session, OAuth2 OIDC client & JWT filter.
  * `RedisConfig.java`: Cấu hình Redis connection, `RedisTemplate`, serializers (Jackson/String) phục vụ caching và rate limiting.
  * `RabbitMqConfig.java` & `RabbitMqConstants.java`: Cấu hình AMQP Message Converter (JSON), Topic/Direct Exchanges, Queues, Routing Keys và Dead Letter Exchanges (DLX).
  * `MinIOConfig.java`: Khởi tạo và cấu hình `MinioClient` kết nối tới MinIO Object Storage.
  * `JacksonConfig.java`: Cấu hình `ObjectMapper` tùy biến, đăng ký module hỗ trợ Java 8 Date/Time (JSR-310), UTC RFC3339.
  * `TransactionConfig.java`: Tùy biến quản lý Transaction, `PlatformTransactionManager`.

* **`security/` (Authentication & Security Components):**
  * `JwtUtils.java`: Tạo, parse, ký và kiểm tra tính hợp lệ của Access Token & Refresh Token (HMAC-SHA256).
  * `JwtFilter.java`: Filter chặn các HTTP request để trích xuất Bearer token, đối soát blacklist và nạp authentication vào `SecurityContextHolder`.
  * `TokenBlacklistService.java`: Quản lý danh sách thu hồi token (revoked tokens) qua Redis.
  * `OidcSuccessHandler.java` & `OidcFailureHandler.java`: Xử lý redirect và tạo JWT sau khi hoàn tất luồng đăng nhập Google OAuth2 / OIDC.

* **`rateLimit/` (Rate Limiting & Anti-bot):**
  * `RateLimitFilter.java`: Filter HTTP chặn và kiểm soát tần suất request theo IP/client identifier.
  * `RedisRateLimiter.java`: Thực thi thuật toán Sliding Window Counter thông qua Redis Lua script (`sliding-window.lua`).
  * `RateLimitPolicy.java`, `RateLimitPolicyProvider.java`, `RateLimitPolicyRegistry.java`: Định nghĩa interface, registry đăng ký dynamic policy giới hạn rate limit cho từng module/endpoint đặc thù (Auth, Catalog, Order, v.v.).

* **`filter/` (HTTP Interceptors / Tracing):**
  * `RequestCorrelationFilter.java`: Gắn và truyền `correlationId` (MDC tracing) qua từng request HTTP để đồng bộ log luồng xử lý.

* **`exception/` (Global Error Handling):**
  * `AppException.java`: Base runtime exception của hệ thống.
  * `GlobalExceptionHandler.java`: `@RestControllerAdvice` tập trung bắt và chuẩn hóa toàn bộ lỗi hệ thống, biến validation error, business error thành định dạng RFC 7807 / RFC 9457 Problem Details.

* **`response/` (Response Formatting):**
  * `ApiResponse.java`: Cấu trúc response chuẩn cho các API trả về thành công (`data`, `status`, `meta`).
  * `ErrorResponse.java`, `ApiProblemFactory.java`, `ApiProblemWriter.java`: Chuẩn hóa cấu trúc Problem Details (`type`, `title`, `status`, `detail`, `instance`, `errors`, `timestamp`, `correlationId`).

---

## 3. Each Module

Mỗi bounded context được đóng gói trong một thư mục module độc lập tại `backend/src/main/java/com/ticketbox/api/module/<module_name>/`. Một module chuẩn có các layer và thư mục sau:

```text
com/ticketbox/api/module/<module_name>/
├── controllers/                                     # HTTP REST Endpoints (@RestController)
├── services/                                        # Business logic interface & implementation
├── repositories/                                    # Spring Data JPA Data Access Object
├── domain/                                          # Thực thể, models, DTOs & exceptions nội bộ module
│   ├── entities/                                    # JPA Entities (@Entity)
│   ├── dtos/                                        # Request/Response DTOs, payload data
│   ├── exception/                                   # Domain-specific exceptions & ErrorCode enum
│   └── mappers/                                     # Mapper chuyển đổi giữa Entity và DTO (nếu có)
├── config/                                          # Cấu hình riêng cho module (RabbitMQ queues/exchanges, RateLimit policies)
├── producers/ / producer/                           # Publisher gửi event/message sang RabbitMQ
├── consumers/ / consumer/                           # Listener nhận và xử lý message từ RabbitMQ (nếu xử lý async trong module)
├── events/                                          # Domain event payload class đại diện sự kiện phát đi
└── schedulers/                                      # Cron job / Scheduled tasks nội bộ (@Scheduled)
```

### Chi tiết các thành phần bên trong:
* **`controllers/`**: Tiếp nhận HTTP request, validate dữ liệu đầu vào (`@Valid`), ủy quyền xử lý cho Service và đóng gói kết quả vào `ApiResponse`.
* **`services/`**: Chứa Service Interface (e.g. `OrderService`) và implementation (`OrderServiceImpl`), thực hiện các nghiệp vụ cốt lõi, quản lý giao dịch (`@Transactional`) và tương tác giữa các module.
* **`repositories/`**: Kế thừa `JpaRepository` thực hiện truy vấn cơ sở dữ liệu Postgres (bao gồm các truy vấn tối ưu, pessimistic locking `PESSIMISTIC_WRITE`).
* **`domain/`**:
  * `entities/`: Các bảng database biểu diễn thành Java class (VD: `Concert`, `SeatZone`, `Order`, `Payment`).
  * `dtos/`: Các Data Transfer Object nhận từ client (`*Request`) hoặc trả ra client (`*Response`), deserializer chuyên biệt.
  * `exception/`: Định nghĩa ngoại lệ của domain (VD: `ConcertNotFoundException`, `PerUserLimitExceededException`) và enum mã lỗi domain (VD: `OrderErrorCode`).
  * `mappers/`: Chuyển đổi dữ liệu 2 chiều giữa Entity và DTO.
* **`config/`**: Khai báo Bean đặc thù của module, định nghĩa topology queue/exchange RabbitMQ (VD: `OrderRabbitConfig`, `PaymentRabbitConfig`) và cấu hình rate limiting (VD: `CatalogAdminRateLimitPolicy`).
* **`producers/` / `producer/`**: Đẩy các message/event bất đồng bộ lên RabbitMQ (VD: `PaymentCompletionPublisher`, `TicketIssuedPublisher`, `OrderProducer`).
* **`consumers/` / `consumer/`**: Nhận event từ các module khác để xử lý nghiệp vụ bất đồng bộ (VD: `TicketPaymentSucceededConsumer` lắng nghe thanh toán thành công để xuất vé; `NotificationConsumer` lắng nghe gửi email).
* **`events/`**: Chứa các DTO/Record sự kiện (VD: `PaymentCompletedEvent`, `TicketIssuedEvent`).
* **`schedulers/`**: Các tiến trình chạy định kỳ tự động (VD: `OrderExpirationScheduler` quét giải phóng vé giữ chỗ quá hạn, `PaymentReconciliationScheduler` đối soát giao dịch).

*(Một số module đặc thù có thêm các folder con riêng biệt: `payment/gateways` tích hợp cổng VNPay, MoMo; `artistbio/ai` tích hợp Gemini AI và trích xuất PDF; `artistbio/storage` xử lý file upload).*

---

## 4. Others Folders

Mô tả chi tiết 2 thành phần dùng chung và tác vụ nền quan trọng:

### 4.1. Shared Module (`com.ticketbox.api.module.shared`)
Cung cấp các công cụ, dịch vụ kỹ thuật nghiệp vụ tái sử dụng xuyên suốt qua nhiều module khác nhau:
* **`cache/`**:
  * `CacheService.java`: Wrapper thao tác với Redis (get, set, expire, delete pattern, evict).
  * `SingleFlight.java`: Kỹ thuật chống hiện tượng Cache Stampede / Thundering Herd bằng cách gộp các request đồng thời truy vấn cùng 1 key thành 1 lần gọi DB duy nhất.
  * `CachedPage.java`: Hỗ trợ serialize/deserialize phân trang `Page<T>` của Spring Data khi lưu vào cache Redis.
* **`idempotency/`**:
  * `IdempotencyService.java`, `IdempotencyHelper.java`, `IdempotencyRecord.java`: Cơ chế đảm bảo tính Idempotency cho các giao dịch quan trọng (Create Order, Payment, Callbacks) sử dụng Redis lock và cache kết quả để ngăn chặn trùng lặp giao dịch khi client retry.
* **`storage/`**:
  * `StorageService.java`: Service trừu tượng hóa việc tải lên, lấy URL và quản lý file lưu trữ trên MinIO Object Storage.
* **`exception/`**:
  * Tập hợp các common business exceptions dùng chung giữa các domain: `BusinessException`, `NotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`.
* **`validation/`**:
  * Xử lý xác thực request chung (`RequestValidationException`).
* **`domain/dtos/`**:
  * Chứa các DTO trao đổi dữ liệu xuyên module (VD: `AuthOtpMessageDTO`).

### 4.2. Worker (`com.ticketbox.api.worker`)
Chứa các thành phần chuyên biệt phục vụ tiến trình background worker độc lập:
* **Mô hình hoạt động:** Dự án hỗ trợ chạy song song hoặc tách rời giữa profile `api` (xử lý HTTP request - port 8082) và profile `worker` (xử lý background jobs/queues - port 8081) từ cùng một mã nguồn (Single JAR).
* **`WorkerPlaceholder.java`**: `CommandLineRunner` kích hoạt khi ứng dụng khởi chạy với profile `@Profile("worker")`, khởi tạo môi trường worker process độc lập chuyên xử lý Async Jobs.
* **`WarmUpWorker/`**:
  * `WarmUpWorkerListener.java`: RabbitMQ Listener (`@RabbitListener`) lắng nghe hàng đợi `catalog.cache.warmup.queue` để thực thi tác vụ làm ấm trước dữ liệu cache của concert (`ConcertWarmUpService`) trong nền mà không gây nghẽn tiến trình web API chính.