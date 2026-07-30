# TicketBox - Project Structure

Tài liệu này mô tả cấu trúc dự án TicketBox theo kiến trúc **Modular Monolith (Single Project - Multi Process)** với tech stack **Spring Boot (Java 21)**.

## 1. Nguyên tắc tổ chức

1. **Spring Boot (Java 21) Single Project**: Backend là **MỘT** Spring Boot project duy nhất đặt tại `backend/`, đóng gói ra **MỘT file JAR duy nhất** (`app.jar`).
2. **Multi-Process Deployment (Profiles)**: File `app.jar` được vận hành thành nhiều process (JVM) độc lập bằng cách truyền các **Spring Profile** hoặc tham số cấu hình khác nhau:
   - `-Dspring.profiles.active=api`: Chạy REST API Server tiếp nhận kết nối HTTP từ Web/Mobile Client, xử lý JPA/DB và Redis Cache.
   - `-Dspring.profiles.active=worker`: Chạy Async Worker lắng nghe RabbitMQ (`@RabbitListener`), xử lý các tác vụ I/O nặng ngầm (OpenAI API, parse CSV/PDF, gửi Email).
3. **Module theo Bounded Context**: Phân chia các package trong `backend/src/main/java/com/ticketbox/api/module/`: Auth, Catalog, Inventory, Order, Payment, Ticket, Guest List, Artist Bio, Notification, Audit.
4. **Loại bỏ Check-in**: Không triển khai tính năng Check-in (Online QR gate scan, Offline SQLite sync, Mobile check-in app).
5. **PostgreSQL là Source of Truth**: Sử dụng Spring Data JPA + Flyway migrations; Redis & RabbitMQ làm hạ tầng phụ trợ.
6. **Cấu hình Email thật (No Mailpit)**: Gửi email xác nhận/nhắc lịch thông qua SMTP thật với các chỗ trống cấu hình (`spring.mail.*`) để người dùng điền sau.

---

## 2. Cấu trúc thư mục dự án

```text
ticket-box/
├── frontend/                                        # Web khán giả + Admin Dashboard (React/Next.js)
│
└── backend/                                         # Spring Boot Modular Monolith (Single JAR)
    ├── pom.xml                                      # Maven POM duy nhất (Java 21, Spring Boot 3.x/4.x)
    ├── docker-compose.yml                           # Postgres 15, Redis 7, RabbitMQ 3.12
    ├── src/
    │   ├── main/
    │   │   ├── java/com/ticketbox/api/
    │   │   │   ├── TicketboxApplication.java        # Spring Boot Main Class
    │   │   │   │
    │   │   │   ├── module/                          # Bounded Context Modules
    │   │   │   │   ├── auth/                        # User, UserAccount, RBAC Roles, AuthController, JwtFilter
    │   │   │   │   ├── catalog/                     # Concert, Venue, SeatZone, CatalogCacheService
    │   │   │   │   ├── inventory/                   # TicketType, Hold/Release Lock, UserTicketTypeCounter
    │   │   │   │   ├── order/                       # Order, OrderItem, ExpireHoldScheduler (@Scheduled)
    │   │   │   │   ├── payment/                     # Payment, VNPay, MoMo, IdempotencyService
    │   │   │   │   ├── ticket/                      # Ticket, QrService (HMAC QR code)
    │   │   │   │   ├── guestlist/                   # Guest, GuestImportJob, GuestImportError
    │   │   │   │   ├── artistbio/                   # ArtistBioJob
    │   │   │   │   ├── notification/                # Notification, ReminderScheduler
    │   │   │   │   └── audit/                       # AuditLog, AuditAspect (@AuditLog)
    │   │   │   │
    │   │   │   ├── worker/                          # Background Worker Components (@Profile("worker"))
    │   │   │   │   ├── listener/                    # RabbitMQ Listeners (@RabbitListener)
    │   │   │   │   └── handler/                     # Handlers (OpenAI, OpenCSV, JavaMailSender)
    │   │   │   │
    │   │   │   └── infrastructure/                  # Infrastructure Configurations
    │   │   │       ├── config/                      # SecurityConfig, RedisConfig, RabbitMQConfig
    │   │   │       └── security/                    # JwtTokenProvider, JwtAuthenticationFilter
    │   │   │
    │   │   └── resources/
    │   │       ├── application.yml                  # Cấu hình chung (DB, Redis, RabbitMQ, Mail Placeholders)
    │   │       ├── application-api.yml              # Profile "api" (Port 8080)
    │   │       ├── application-worker.yml           # Profile "worker" (Port 8081)
    │   │       └── db/migration/                    # Flyway migrations (V1__init_schema.sql)
    │   └── test/
```

---

## 3. Mapping Module với Specs

| Module | Specs / Tính năng | Bảng dữ liệu chính |
| --- | --- | --- |
| `auth` | Auth RBAC, JWT, Redis denylist | `users`, `audit_logs` |
| `catalog` | Concert Catalog, Venue, Seat Zone SVG | `venues`, `concerts`, `seat_zones`, `ticket_types` |
| `inventory` | Ticket Inventory, Hold/Release vé, Per-user limit | `ticket_types`, `user_ticket_type_counters`, `orders` |
| `order` | Order Checkout, Hold Expiry Scheduler | `orders`, `order_items` |
| `payment` | VNPay/MoMo Sandbox, Webhook, Idempotency | `payments`, `orders` |
| `ticket` | E-ticket QR issuing & viewing | `tickets` |
| `guestlist` | CSV Guest List Import | `guest_import_jobs`, `guest_list`, `guest_import_errors` |
| `artistbio` | AI Artist Bio Summary từ PDF | `artist_bio_jobs`, `concerts.artist_bio` |
| `notification` | Email E-ticket HTML, Nhắc lịch 24h | `notifications` |
| `audit` | Audit Logging cho thao tác quản trị | `audit_logs` |

---

## 4. Ranh Giới Trách Nhiệm Chi Tiết

- **Process API (`-Dspring.profiles.active=api`)**: Tiếp nhận HTTP REST request từ Web Frontend, validate input, kiểm tra phân quyền (`@PreAuthorize`), thực thi business logic, quản lý Transaction JPA, thao tác Redis Cache, và đẩy Message vào RabbitMQ cho các tác vụ bất đồng bộ.
- **Process Worker (`-Dspring.profiles.active=worker`)**: Lắng nghe RabbitMQ message (`@RabbitListener`), xử lý các tác vụ CPU/IO-bound nặng (gửi Email SMTP thật, gọi OpenAI API, parse file CSV), cập nhật trạng thái công việc trực tiếp vào PostgreSQL.
