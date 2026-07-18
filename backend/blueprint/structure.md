# TicketBox - Project Structure

Tài liệu này mô tả cấu trúc mục tiêu của `ticket-box-app/` theo kiến trúc **Event-Driven Modular Monolith** với tech stack **Spring Boot (Java)**. Đây là blueprint để triển khai dần, không có nghĩa là mọi thư mục đã tồn tại trong repo hiện tại.

## 1. Nguyên tắc tổ chức

1. **Java + Spring Boot mặc định** cho toàn bộ backend. Mỗi app backend là một Spring Boot project con trong Maven multi-module.
2. **Module theo use case/API design**, bám các file trong `blueprint/api-design/`: Auth/RBAC, Catalog, Inventory, Order Checkout, E-ticket, Check-in, Guest List, Artist Bio, Notification.
3. **Modular monolith, không microservice hóa sớm**. Các module nằm chung `api-server`, giao tiếp nội bộ bằng Service call và event contract rõ ràng.
4. **PostgreSQL là source of truth** thông qua Spring Data JPA + Flyway migrations; Redis/RabbitMQ/MinIO chỉ là hạ tầng phụ trợ.
5. **Worker stateless, không chạm DB trực tiếp**. `worker-server` là Spring Boot app riêng, **không có JPA entity/repository**. Worker chỉ làm tác vụ I/O ngoài (gọi OpenAI, parse CSV/PDF, gửi email) rồi ghi kết quả về `api-server` qua các endpoint `/internal/*` (HTTP + `X-Internal-Token`) hoặc nhận việc qua RabbitMQ. Mọi truy cập PostgreSQL đều thuộc về `api-server`.
6. **Job thuần-DB nằm ở api-server**. Các scheduler chỉ query/update DB mà không có I/O ngoài (expire hold, reminder 24h) chạy `@Scheduled` **in-process trong api-server**, không đẩy sang worker.
7. **Giao tiếp worker → api-server dùng retry, không circuit breaker**. CB chỉ dành cho cổng thanh toán bên thứ ba (VNPAY/MoMo). Lỗi gọi nội bộ xử lý bằng `@Retryable` + idempotency key.
8. **Không tạo abstraction chung khi chưa cần**. Mỗi module có Controller/Service/Repository/Entity/dto; chỉ đưa vào `shared/` khi thực sự dùng bởi nhiều module.
9. **Controller là ranh giới HTTP của module**. Controller validate input và gọi Service; Service chứa business logic và `@Transactional`; Repository chỉ query DB, không biết đến HTTP concern.

## 2. Cấu trúc mục tiêu

```text
ticket-box-app/
│
├── apps/
│   ├── web/                                         # Web khán giả + Admin Dashboard (React/Next.js)
│   │   ├── src/
│   │   │   ├── routes/                              # React Router route components
│   │   │   ├── features/                            # UI theo domain: catalog, checkout, admin...
│   │   │   ├── components/                          # Component dùng chung
│   │   │   ├── lib/                                 # API client, auth client, formatters
│   │   │   └── styles/
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   ├── mobile-checkin/                              # React Native/Expo app cho nhân sự soát vé
│   │   ├── src/
│   │   │   ├── screens/
│   │   │   ├── features/
│   │   │   │   ├── bootstrap/                       # Tải device/gate/concert config
│   │   │   │   ├── preload/                         # Tải valid tickets/guests về SQLite
│   │   │   │   ├── scanner/                         # QR scan online/offline
│   │   │   │   └── sync/                            # Offline batch sync lên server
│   │   │   ├── local-db/                            # SQLite schema + repository
│   │   │   └── lib/
│   │   ├── app.json
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   ├── api-server/                                  # Spring Boot: modular monolith API
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/ticketbox/api/
│   │   │   │   │   ├── ApiServerApplication.java
│   │   │   │   │   │
│   │   │   │   │   ├── module/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── auth/                    # JWT, users.role, Redis denylist
│   │   │   │   │   │   │   ├── AuthController.java
│   │   │   │   │   │   │   ├── AuthService.java
│   │   │   │   │   │   │   ├── UserRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   └── UserEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │       ├── LoginRequest.java
│   │   │   │   │   │   │       └── TokenResponse.java
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── catalog/                 # Concert, venue, seat zones, public metadata
│   │   │   │   │   │   │   ├── CatalogController.java
│   │   │   │   │   │   │   ├── CatalogService.java
│   │   │   │   │   │   │   ├── CatalogRepository.java
│   │   │   │   │   │   │   ├── CatalogCacheService.java  # Cache-aside + invalidation
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── ConcertEntity.java
│   │   │   │   │   │   │   │   ├── VenueEntity.java
│   │   │   │   │   │   │   │   └── SeatZoneEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── inventory/               # Hold/release/confirm/admin-adjust + per-user counter
│   │   │   │   │   │   │   ├── InventoryController.java
│   │   │   │   │   │   │   ├── InventoryInternalController.java  # /internal/inventory/releases, /payment-confirmations
│   │   │   │   │   │   │   ├── InventoryService.java
│   │   │   │   │   │   │   ├── InventoryRepository.java
│   │   │   │   │   │   │   ├── UserTicketCounterRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── TicketTypeEntity.java
│   │   │   │   │   │   │   │   └── UserTicketTypeCounterEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── order/                   # Checkout, order status, ownership
│   │   │   │   │   │   │   ├── OrderController.java
│   │   │   │   │   │   │   ├── OrderInternalController.java  # POST /internal/orders/{id}/expire
│   │   │   │   │   │   │   ├── OrderService.java
│   │   │   │   │   │   │   ├── ExpireHoldScheduler.java      # @Scheduled: quét order HELD hết hold_expires_at (thuần DB)
│   │   │   │   │   │   │   ├── OrderRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── OrderEntity.java
│   │   │   │   │   │   │   │   └── OrderItemEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── payment/                 # VNPAY/MoMo sandbox, webhook, idempotency
│   │   │   │   │   │   │   ├── PaymentController.java
│   │   │   │   │   │   │   ├── PaymentService.java
│   │   │   │   │   │   │   ├── PaymentRepository.java
│   │   │   │   │   │   │   ├── WebhookHandler.java
│   │   │   │   │   │   │   ├── IdempotencyService.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   └── PaymentEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── ticket/                  # E-ticket QR issuing/viewing
│   │   │   │   │   │   │   ├── TicketController.java         # GET /me/tickets, /me/tickets/{id}/qr
│   │   │   │   │   │   │   ├── TicketInternalController.java # /internal/orders/{id}/tickets/issue, /tickets/resolve-qr
│   │   │   │   │   │   │   ├── TicketService.java
│   │   │   │   │   │   │   ├── TicketRepository.java
│   │   │   │   │   │   │   ├── QrService.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   └── TicketEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── checkin/                 # Online scan, guest scan, offline batch sync
│   │   │   │   │   │   │   ├── CheckinController.java
│   │   │   │   │   │   │   ├── CheckinService.java
│   │   │   │   │   │   │   ├── CheckinRepository.java
│   │   │   │   │   │   │   ├── GateZoneValidator.java       # Validate gate ↔ seat_zone mapping
│   │   │   │   │   │   │   ├── OfflineSyncService.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── CheckinGateEntity.java
│   │   │   │   │   │   │   │   ├── CheckinGateZoneEntity.java
│   │   │   │   │   │   │   │   ├── CheckinDeviceEntity.java
│   │   │   │   │   │   │   │   ├── CheckinLogEntity.java
│   │   │   │   │   │   │   │   ├── OfflineCheckinBatchEntity.java
│   │   │   │   │   │   │   │   └── OfflineCheckinItemEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── guestlist/               # CSV import jobs, guest CRUD/search
│   │   │   │   │   │   │   ├── GuestListController.java
│   │   │   │   │   │   │   ├── GuestListInternalController.java  # POST /internal/guest-list/import (bulk upsert)
│   │   │   │   │   │   │   ├── GuestListService.java
│   │   │   │   │   │   │   ├── GuestListRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── GuestListEntity.java
│   │   │   │   │   │   │   │   ├── GuestImportJobEntity.java
│   │   │   │   │   │   │   │   └── GuestImportErrorEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── artistbio/               # AI bio jobs + concerts.artist_bio
│   │   │   │   │   │   │   ├── ArtistBioController.java         # Upload PDF, trigger job → publish RabbitMQ
│   │   │   │   │   │   │   ├── ArtistBioInternalController.java # POST /internal/artist-bio-jobs/{id}/result
│   │   │   │   │   │   │   ├── ArtistBioService.java
│   │   │   │   │   │   │   ├── ArtistBioRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   └── ArtistBioJobEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── notification/            # notifications table + enqueue
│   │   │   │   │   │   │   ├── NotificationController.java
│   │   │   │   │   │   │   ├── NotificationInternalController.java # POST /internal/notifications/{id}/status
│   │   │   │   │   │   │   ├── NotificationService.java          # enqueue: ghi row + publish RabbitMQ
│   │   │   │   │   │   │   ├── ReminderScheduler.java            # @Scheduled: scan concert < 24h → enqueue (thuần DB)
│   │   │   │   │   │   │   ├── NotificationRepository.java
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   └── NotificationEntity.java
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │
│   │   │   │   │   │   └── audit/                   # audit_logs: ghi từ nhiều module (in-process)
│   │   │   │   │   │       ├── AuditService.java                # log(action, entityType, entityId, actor)
│   │   │   │   │   │       ├── AuditLogRepository.java
│   │   │   │   │   │       └── entity/
│   │   │   │   │   │           └── AuditLogEntity.java
│   │   │   │   │   │
│   │   │   │   │   ├── shared/
│   │   │   │   │   │   ├── config/
│   │   │   │   │   │   │   ├── SecurityConfig.java          # Spring Security + JWT filter chain
│   │   │   │   │   │   │   ├── RedisConfig.java             # RedisTemplate, StringRedisTemplate
│   │   │   │   │   │   │   └── RabbitMQConfig.java          # Exchange, Queue, Binding declarations
│   │   │   │   │   │   ├── security/
│   │   │   │   │   │   │   ├── JwtAuthFilter.java           # OncePerRequestFilter: verify + set context
│   │   │   │   │   │   │   ├── JwtService.java              # Sign, verify, extract claims
│   │   │   │   │   │   │   ├── InternalTokenFilter.java     # Verify X-Internal-Token cho route /internal/*
│   │   │   │   │   │   │   └── RateLimitFilter.java         # Bucket4j Token Bucket per User ID
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │   │   │   │   │   │   └── ApiException.java
│   │   │   │   │   │   └── response/
│   │   │   │   │   │       └── ApiResponse.java             # Envelope {success, data, error}
│   │   │   │   │   │
│   │   │   │   │   └── infrastructure/
│   │   │   │   │       ├── redis/
│   │   │   │   │       │   ├── RedisCache.java              # get/set/evict với TTL
│   │   │   │   │       │   └── IdempotencyStore.java        # IN_PROGRESS / COMPLETED state
│   │   │   │   │       ├── rabbitmq/
│   │   │   │   │       │   ├── EventPublisher.java          # RabbitTemplate wrapper
│   │   │   │   │       │   └── QueueNames.java              # Hằng số tên queue/exchange/routing key
│   │   │   │   │       └── storage/
│   │   │   │   │           └── MinioStorageService.java     # upload/download/presigned URL
│   │   │   │   │
│   │   │   │   └── resources/
│   │   │   │       ├── application.yml
│   │   │   │       ├── application-dev.yml
│   │   │   │       └── db/migration/                        # Flyway migrations
│   │   │   │           ├── V1__init_schema.sql
│   │   │   │           └── V2__seed_data.sql
│   │   │   └── test/
│   │   │       └── java/com/ticketbox/api/
│   │   └── pom.xml
│   │
│   └── worker-server/                               # Spring Boot: chỉ I/O ngoài, KHÔNG JPA/entity
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/ticketbox/worker/
│       │   │   │   ├── WorkerApplication.java
│       │   │   │   │
│       │   │   │   ├── client/
│       │   │   │   │   └── InternalApiClient.java           # RestClient gọi api-server /internal/* (@Retryable, X-Internal-Token)
│       │   │   │   │
│       │   │   │   ├── aibio/                               # I/O ngoài: OpenAI + đọc PDF
│       │   │   │   │   ├── AiBioListener.java               # @RabbitListener: nhận job_id + presigned URL của PDF
│       │   │   │   │   ├── PdfTextExtractor.java            # Tải PDF qua presigned URL (HTTP GET), trích/làm sạch text
│       │   │   │   │   ├── OpenAiClient.java                # Gọi OpenAI sinh bio
│       │   │   │   │   └── AiBioHandler.java                # Orchestrate → POST /internal/artist-bio-jobs/{id}/result
│       │   │   │   │
│       │   │   │   ├── guestimport/                         # I/O ngoài: đọc + parse CSV
│       │   │   │   │   ├── GuestImportScheduler.java        # @Scheduled(cron="0 0 2 * * *"): quét thư mục drop của nhãn hàng
│       │   │   │   │   ├── CsvDropStorageClient.java        # Read-only client cho thư mục CSV drop (SFTP/bucket riêng)
│       │   │   │   │   ├── CsvParser.java                   # Parse + validate từng dòng, gom lỗi
│       │   │   │   │   └── GuestImportHandler.java          # POST /internal/guest-list/import (rows hợp lệ + lỗi)
│       │   │   │   │
│       │   │   │   └── notification/                        # I/O ngoài: gửi email/push
│       │   │   │       ├── NotificationListener.java        # @RabbitListener: nhận payload từ message
│       │   │   │       ├── EmailClient.java                 # Gửi SMTP/SendGrid
│       │   │   │       └── NotificationHandler.java         # Gửi → POST /internal/notifications/{id}/status
│       │   │   │
│       │   │   └── resources/
│       │   │       └── application.yml                      # RabbitMQ, OpenAI key, internal token + URL, SMTP, CSV drop folder
│       │   └── test/
│       └── pom.xml
│
├── nginx/
│   ├── nginx.conf
│   └── conf.d/
│       ├── rate-limit.conf                          # limit_req_zone cho IP-level (Leaky Bucket)
│       └── upstream.conf
│
├── docker/
│   ├── Dockerfile.api
│   ├── Dockerfile.worker
│   └── docker-compose.yml                           # api, worker, postgres, redis, rabbitmq, minio, nginx
│
└── pom.xml                                          # Parent POM (Maven multi-module)
```

## 3. Mapping module với tài liệu API/spec

| Module | API design | Specs chính | Bảng dữ liệu chính |
| --- | --- | --- | --- |
| `auth` | `auth-rbac-api.md` | `08-auth-rbac.md` | `users`, `audit_logs` |
| `catalog` | `catalog-api.md` | `09-concert-catalog.md`, `14-caching.md` | `venues`, `concerts`, `seat_zones`, `ticket_types` |
| `inventory` | `inventory-api.md` | `01-ticket-inventory.md`, `02-per-user-ticket-limit.md` | `ticket_types`, `user_ticket_type_counters`, `orders`, `order_items` |
| `order` | `order-checkout-api.md` | `10-order-checkout.md` | `orders`, `order_items` |
| `payment` | `order-checkout-api.md` | `03-payment-idempotency.md` | `payments`, `orders` |
| `ticket` | `e-ticket-api.md` | `11-e-ticket-qr.md` | `tickets` |
| `checkin` | `check-in-api.md` | `04-offline-checkin-sync.md`, `12-checkin-online.md`, `13-guest-checkin.md` | `checkin_devices`, `checkin_gates`, `checkin_gate_zones`, `checkin_logs`, `offline_checkin_batches`, `offline_checkin_items` |
| `guestlist` | `guest-list-api.md` | `05-guest-list-import.md`, `13-guest-checkin.md` | `guest_import_jobs`, `guest_list`, `guest_import_errors` |
| `artistbio` | `artist-bio-api.md` | `06-artist-bio-ai.md` | `artist_bio_jobs`, `concerts.artist_bio` |
| `notification` | `notification-api.md` | `07-notification.md` | `notifications` |
| `audit` | (cross-cutting) | `16-audit-logging.md` | `audit_logs` |

> **Internal endpoints (`/internal/*`):** mỗi `*InternalController` chỉ nhận request từ worker-server (header `X-Internal-Token`, không có user JWT), không expose ra public. Đây là cách worker ghi kết quả về DB mà bản thân worker không cần JPA entity.

## 4. Quy ước đặt file trong module

| File | Vai trò | Annotation chính |
| --- | --- | --- |
| `*Controller.java` | Ranh giới HTTP public: validate input, gọi service, trả response | `@RestController`, `@RequestMapping` |
| `*InternalController.java` | Endpoint `/internal/*` chỉ cho worker gọi; xác thực `X-Internal-Token` | `@RestController` |
| `*Service.java` | Business logic, transaction boundary, gọi repository | `@Service`, `@Transactional` |
| `*Scheduler.java` | Cron job **thuần DB** chạy in-process trong api-server (expire hold, reminder) | `@Scheduled` |
| `*Repository.java` | Query DB qua JPA; không biết đến HTTP concern | `@Repository`, extends `JpaRepository` |
| `entity/*Entity.java` | JPA mapping sang bảng PostgreSQL | `@Entity`, `@Table` |
| `dto/` | Request/Response object (Java `record` hoặc class thuần) | — |
| `*CacheService.java` | Cache-aside/invalidation riêng module | `@Component` + `RedisTemplate` |
| **worker-server** | | |
| `*Listener.java` | Consume event từ RabbitMQ | `@RabbitListener` |
| `*Scheduler.java` | Cron job kích hoạt tác vụ I/O ngoài (đọc CSV...) | `@Scheduled` |
| `*Client.java` | Gọi service ngoài (OpenAI, SMTP) hoặc `InternalApiClient` gọi api-server | `@Component`, `@Retryable` |
| `*Handler.java` | Orchestrate: chạy I/O ngoài rồi đẩy kết quả về api-server qua `/internal/*` | `@Component` |

## 5. Ranh giới trách nhiệm quan trọng

- `api-server` nhận HTTP qua Controller, validate, kiểm tra quyền (`@PreAuthorize`), gọi Service và trả response.
- `Controller` không query DB trực tiếp; mọi truy cập dữ liệu đi qua Service → Repository.
- `Repository` không import HTTP concern (không biết đến request/response/status code).
- `Service` là nơi duy nhất đặt `@Transactional`; không mix HTTP logic vào đây.
- `worker-server` chỉ làm tác vụ I/O ngoài (OpenAI, parse file, SMTP); **không có JPA entity/repository**, không kết nối PostgreSQL. Ghi kết quả về api-server qua `InternalApiClient` (HTTP `/internal/*` + `@Retryable`).
- Scheduler thuần-DB (`ExpireHoldScheduler`, `ReminderScheduler`) chạy in-process trong api-server, **không** đặt ở worker.
- `infrastructure/redis/` chứa Redis primitives cho cache, rate limit, denylist, idempotency.
- `infrastructure/rabbitmq/` chứa `RabbitTemplate` wrapper và hằng số tên queue/exchange/routing key.
- `infrastructure/storage/` chứa thao tác MinIO; module chỉ truyền object key/presigned URL.
- Flyway migrations trong `resources/db/migration/` là source of truth cho schema; JPA entity phải khớp với SQL migration.

## 6. MVP core vs Extended features

| Tính năng | Nhóm | Ghi chú |
| --- | --- | --- |
| Auth (đăng ký, đăng nhập, JWT) | **MVP core** | Bắt buộc cho mọi flow |
| Catalog: xem concert, danh sách vé | **MVP core** | |
| Inventory: hold + release vé, per-user limit | **MVP core** | Pessimistic lock + user_ticket_type_counters |
| Order + Payment (VNPAY/MoMo sandbox) | **MVP core** | Idempotency key, webhook |
| E-ticket QR generation | **MVP core** | |
| Check-in online (quét QR, xác nhận) | **MVP core** | |
| Caching concert/inventory (Redis) | **MVP core** | Cache-aside, TTL ngắn cho inventory |
| Rate Limiting (Nginx + Bucket4j) | **MVP core** | |
| Notification: email sau mua vé | **MVP core** | |
| Check-in offline (SQLite + sync) | **Extended** | Demo được nhưng phức tạp; để sau khi core xong |
| AI Artist Bio (PDF upload + OpenAI) | **Extended** | Worker async; không ảnh hưởng core nếu lỗi |
| CSV Guest List import (cron 02:00 AM) | **Extended** | ETL worker; tách biệt hoàn toàn |
| Notification nhắc lịch 24h trước | **Extended** | `ReminderScheduler` (@Scheduled in-process api-server) scan DB → enqueue; worker chỉ gửi |
| Circuit Breaker cho payment gateway | **Extended** | Thêm sau khi core ổn định |
| Admin dashboard thống kê doanh thu | **Extended** | |

## 7. Lưu ý

- Các thành phần chính trong `design.md` và cách tổ chức module trong `structure.md` không mâu thuẫn với nhau.
- 4 module trong `design.md` ở mức kiến trúc logic cấp cao:
  - Catalog → module `catalog`
  - Ticketing & Order → module `inventory` + `order` + `ticket`
  - Payment → module `payment`
  - Check-in → module `checkin`
- `guestlist` nằm gần Check-in vì phục vụ guest VIP tại cổng.
- `artistbio`: api-server nhận upload PDF, tạo job, publish RabbitMQ; worker `AiBioListener` trích PDF + gọi OpenAI rồi POST kết quả về `/internal/artist-bio-jobs/{id}/result`.
- `notification`: api-server `NotificationService` ghi row + publish RabbitMQ; worker `NotificationListener` gửi email thật rồi POST trạng thái về `/internal/notifications/{id}/status`.
- **Phân định worker vs api-server (Option 3):** việc gì chạm DB → api-server; việc gì gọi I/O ngoài (OpenAI/SMTP/parse file) → worker. Worker và api-server trao đổi qua RabbitMQ (giao việc) và `/internal/*` (ghi kết quả).
- Internal endpoint tổng hợp worker gọi: `/internal/inventory/releases`, `/internal/inventory/payment-confirmations`, `/internal/orders/{id}/tickets/issue`, `/internal/artist-bio-jobs/{id}/result`, `/internal/guest-list/import`, `/internal/notifications/{id}/status`. Riêng `POST /internal/orders/{id}/expire` do `ExpireHoldScheduler` in-process gọi, không qua worker.
- **Truy cập file ở worker (không giữ credential MinIO chung):**
  - PDF press kit: organizer upload qua api-server → MinIO. api-server sinh **presigned URL** và đính kèm vào RabbitMQ event; worker chỉ HTTP GET, không cần MinIO SDK.
  - CSV khách mời: nằm ở **thư mục drop riêng của nhãn hàng** (SFTP/bucket tách biệt). `CsvDropStorageClient` của worker có quyền read-only riêng cho thư mục này — đây là nguồn dữ liệu một chiều, độc lập với MinIO của app.
