# TicketBox - Project Structure

- TicketBox follow **Modular Monolith** structure

## 2. Project structure

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
    │   │   │   │   ├── order/                       # Order, OrderItem, ExpireHoldScheduler (@Scheduled)
    │   │   │   │   ├── payment/                     # Payment, VNPay, MoMo, IdempotencyService
    │   │   │   │   ├── ticket/                      # Ticket, QrService (HMAC QR code)
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
    │   │       └── db/migration/                    # Flyway migrations
    │   └── test/
```