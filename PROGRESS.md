# Task Progress

- File này được sử dụng để theo dõi một task được thực hiện qua nhiều Session.
- Mỗi khi được yêu cầu kết thúc một Session cần tổng kết các thông tin: Goal, Completed, Commits, Files or artifacts updated, Known risk or unresolved issue, Next best step.
- Mỗi khi bắt đầu 1 task mới mà file vẫn còn chứa progress của task cũ thì cần tôi xác nhận có nên xóa ngay hay kiểm tra gì khác nữa không.

## Task Description
- Name: Khắc phục các lỗi unit test khi chạy `./mvnw test`
- Description: Điều tra và sửa 4 ca kiểm thử unit test đang thất bại do chưa đồng bộ assertion sau các đợt refactor cấu trúc Exception sang `BusinessException` và chuẩn phản hồi RFC 7807 `ProblemDetail`.
- Goal: `./mvnw test` thực thi thành công toàn bộ test suite (0 failure, 0 error).
- Acceptance criteria:
  - `RateLimitFilterTest`: cập nhật assertion kiểm tra trường `code` theo chuẩn `ProblemDetail` (top-level thay vì envelope cũ `error.code`).
  - `AdminConcertServiceTest`: cập nhật assertion bắt `CatalogAccessDeniedException` và `ConcertStateConflictException` thay vì `AppException`.
  - `PublicConcertServiceTest`: cập nhật assertion bắt `ConcertNotFoundException` thay vì `AppException`.
  - Chạy `./mvnw test` đạt 100% passed (184/184 tests passed).

## Session Logs

- Session 001
  - Goal: Khắc phục toàn bộ 4 lỗi unit test và đảm bảo toàn bộ suite test pass 100%.
  - Completed:
    - Đã đọc và đối chiếu quy chuẩn tại `AGENT.md`, `DESISIONS.md`, `PROGRESS.md` và `blueprint/exception.md`.
    - Phân tích nguyên nhân và lập plan được người dùng phê duyệt.
    - Sửa `RateLimitFilterTest.java`: cập nhật assertion đọc trường `code` ở root level của RFC 7807 `ProblemDetail`.
    - Sửa `AdminConcertServiceTest.java`: thay thế assertion `AppException` bằng `CatalogAccessDeniedException` và `ConcertStateConflictException`, loại bỏ import `AppException` và `HttpStatus`.
    - Sửa `PublicConcertServiceTest.java`: thay thế assertion `AppException` bằng `ConcertNotFoundException`, loại bỏ import `AppException` và `HttpStatus`.
    - Chạy lại `./mvnw test`: **184/184 tests passed, 0 failures, 0 errors, 2 skipped** (BUILD SUCCESS).
  - Commits: Chưa commit (sẵn sàng commit sau khi người dùng review).
  - Files or artifacts updated:
    - `backend/src/test/java/com/ticketbox/api/infrastructure/rateLimit/RateLimitFilterTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/catalog/services/AdminConcertServiceTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/catalog/services/PublicConcertServiceTest.java`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có rủi ro nào chưa giải quyết.
  - Next best step:
    - Báo cáo kết quả và commit thay đổi khi người dùng yêu cầu.