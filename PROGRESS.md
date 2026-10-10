# Progress Tracking

File này ghi nhận tiến độ thực hiện các task, trạng thái kiểm thử và kế hoạch tiếp theo của dự án TicketBox theo quy định tại `AGENT.md`.

---

## Session: 2026-10-11 - Cập nhật tài liệu cấu trúc dự án (`blueprint/structure.md`)

### 1. Mục tiêu phiên làm việc
- Khảo sát thực tế toàn bộ cấu trúc dự án backend (Spring Boot Modular Monolith), frontend và các thư mục liên quan.
- Cập nhật chi tiết tài liệu [blueprint/structure.md](file:///home/le-nguyen-quoc-thai/src/my-project/Ticket-Box/blueprint/structure.md) đáp ứng đầy đủ 4 yêu cầu:
  1. **Project high level structure**: Khái quát các thành phần cấp cao (`backend`, `frontend`, `blueprint`, `nginx`, `postman`, `docker-compose.yml`, `init.sh`).
  2. **Infrastructure folder**: Liệt kê và phân loại đầy đủ các nhóm cấu hình/thành phần nền tảng (`config`, `security`, `rateLimit`, `filter`, `exception`, `response`).
  3. **Each Module**: Mô tả cấu trúc các thư mục/tập tin bên trong một module chuẩn (`controllers`, `services`, `repositories`, `domain` (entities, dtos, exception, mappers), `config`, `producers`, `consumers`, `events`, `schedulers`) và các thành phần mở rộng (`gateways`, `ai`, `storage`).
  4. **Others folders**: Mô tả chi tiết vai trò của `com.ticketbox.api.module.shared` (cache, SingleFlight, idempotency, storage, validation, exceptions) và `com.ticketbox.api.worker` (cơ chế đa profile `worker` vs `api`, `WarmUpWorkerListener`, `WorkerPlaceholder`).

---

### 2. Các công việc đã thực hiện
- [x] Khảo sát cây thư mục codebase backend Java (`com.ticketbox.api`), cấu trúc tài nguyên `src/main/resources` và `frontend`.
- [x] Rà soát các cấu hình hạ tầng (`MinIOConfig`, `RedisConfig`, `RabbitMqConfig`, `SecurityConfig`, `TransactionConfig`, `JacksonConfig`).
- [x] Rà soát cơ chế bảo mật & anti-bot (`JwtUtils`, `JwtFilter`, `TokenBlacklistService`, `OidcSuccessHandler`, `RedisRateLimiter`, `RateLimitFilter`).
- [x] Rà soát cấu trúc tất cả các Bounded Context Modules (`auth`, `catalog`, `order`, `payment`, `ticket`, `artistbio`, `notification`, `audit`, `guestlist`).
- [x] Rà soát module `shared` (`CacheService`, `SingleFlight`, `IdempotencyService`, `StorageService`, v.v.) và package `worker` (`WarmUpWorkerListener`).
- [x] Cập nhật hoàn chỉnh nội dung file [blueprint/structure.md](file:///home/le-nguyen-quoc-thai/src/my-project/Ticket-Box/blueprint/structure.md).
- [x] Tinh chỉnh phần **Project High Level Structure**: Liệt kê rõ ràng `infrastructure/`, `worker/` và chi tiết toàn bộ các thư mục module bên trong `module/` (`artistbio`, `audit`, `auth`, `catalog`, `guestlist`, `notification`, `order`, `payment`, `shared`, `ticket`).

---

### 3. Trạng thái xác minh (Verification Status)
- File tài liệu đã được ghi và xác minh định dạng Markdown, đường dẫn file và cấu trúc package chính xác 100% khớp với cấu trúc thực tế của codebase.
- Không gây ảnh hưởng hay xung đột với mã nguồn của backend và frontend.

---

### 4. Bước tiếp theo (Next Steps)
- Tiếp tục thực hiện các nhiệm vụ phát triển/hoàn thiện tính năng hoặc refactor tài liệu khác theo yêu cầu tiếp theo của người dùng.
