# Task Progress

- File này được sử dụng để theo dõi một task được thực hiện qua nhiều Session.
- Mỗi khi được yêu cầu kết thúc một Session cần tổng kết các thông tin: Goal, Completed, Commits, Files or artifacts updated, Known risk or unresolved issue, Next best step.
- Mỗi khi bắt đầu 1 task mới mà file vẫn còn chứa progress của task cũ thì cần tôi xác nhận có nên xóa ngay hay kiểm tra gì khác nữa không.

## Task Description
- Name: Dùng JsonNullable cho API PATCH thay cho Set<String> ở các DTO
- Description: Thay thế cơ chế theo dõi trường cập nhật thủ công `Set<String> suppliedFields` và các custom setter bằng thư viện chuẩn `org.openapitools:jackson-databind-nullable` (`JsonNullable<T>`) cho toàn bộ DTO của API PATCH và cập nhật các Service tương ứng theo dạng từng lát cắt (slices).
- Goal: Chuẩn hóa cơ chế partial update (HTTP PATCH / RFC 7396), tích hợp `JsonNullableModule` vào Jackson ObjectMapper, cập nhật `ConcertServiceImpl` xử lý tường minh qua `isPresent()`, cập nhật tài liệu kiến trúc `DESISIONS.md`, và kiểm chứng 100% test suite pass (0 failure, 0 error).
- Acceptance criteria:
  - Thư viện `org.openapitools:jackson-databind-nullable:0.2.6` được thêm vào `pom.xml` và đăng ký `JsonNullableModule` trong `JacksonConfig`.
  - Quyết định kiến trúc 02 được ghi nhận vào `DESISIONS.md` và `.agents/desisions/02-json-nullable-partial-update.md`.
  - Các DTO phục vụ PATCH (`UpdateConcertRequest`, `UpdateSeatZoneRequest`, `UpdateTicketTypeRequest`) được chuyển sang dùng `JsonNullable<T>`, loại bỏ hoàn toàn `Set<String> suppliedFields` và các setter thủ công.
  - Service `ConcertServiceImpl` cập nhật các hàm `updateConcert`, `updateSeatZone`, `updateTicketType` kiểm tra tường minh qua `request.get<Field>().isPresent()`.
  - Toàn bộ test suite `./mvnw test` đạt 100% passed (186/186 tests passed, 0 failures, 0 errors, 2 skipped).
  - Cập nhật `NoteMVC.md` hoàn thành mục "Dùng JsonNullable cho PATCH".

## Session Logs

- Session 001
  - Goal: Khảo sát codebase, thống nhất yêu cầu qua `interview-me`, triển khai refactor theo 4 lát cắt mỏng (slices) và kiểm thử hồi quy toàn bộ hệ thống.
  - Completed:
    - Áp dụng skill `interview-me`: Làm rõ việc dùng thư viện chuẩn `org.openapitools:jackson-databind-nullable` (`JsonNullable<T>`), giữ cơ chế kiểm tra tường minh trong Service (không dùng MapStruct `@MappingTarget` tự động).
    - Lát cắt 1 (Infrastructure & Architecture Decision):
      - Thêm dependency `org.openapitools:jackson-databind-nullable:0.2.6` vào `backend/pom.xml`.
      - Đăng ký `new JsonNullableModule()` vào `JacksonConfig.java`.
      - Tạo tài liệu Quyết định kiến trúc 02 tại `.agents/desisions/02-json-nullable-partial-update.md` và liên kết trong `DESISIONS.md`.
    - Lát cắt 2 (Catalog Concert PATCH):
      - Refactor `UpdateConcertRequest.java`: chuyển các trường sang `JsonNullable<T> = JsonNullable.undefined()`, loại bỏ `suppliedFields` và các setter thủ công, cấu hình `@JsonDeserialize(contentUsing = Rfc3339UtcLocalDateTimeDeserializer.class)` cho trường thời gian.
      - Refactor `ConcertServiceImpl.java` phương thức `updateConcert`: kiểm tra `request.get<Field>() != null && request.get<Field>().isPresent()`.
      - Cập nhật test `ConcertServiceTest.java` và `JacksonConfigTest.java`. Chạy test pass 100%.
    - Lát cắt 3 (Catalog SeatZone & TicketType PATCH):
      - Refactor `UpdateSeatZoneRequest.java`: chuyển sang `JsonNullable<T>`, loại bỏ `suppliedFields` và custom setters.
      - Refactor `UpdateTicketTypeRequest.java`: chuyển sang `JsonNullable<T>`, loại bỏ `suppliedFields` và custom setters.
      - Refactor `ConcertServiceImpl.java` các phương thức `updateSeatZone` và `updateTicketType` tương ứng.
      - Bổ sung test case kiểm tra deserialization của `UpdateSeatZoneRequest` và `UpdateTicketTypeRequest` trong `JacksonConfigTest.java`.
    - Lát cắt 4 (Verification & Documentation):
      - Chạy `./mvnw test`: **186/186 tests passed, 0 failures, 0 errors, 2 skipped** (BUILD SUCCESS).
      - Cập nhật `NoteMVC.md`: đánh dấu `Dùng JsonNullable cho PATCH - complete`.
  - Commits: Sẵn sàng commit sau khi người dùng review.
  - Files or artifacts updated:
    - `backend/pom.xml`
    - `backend/src/main/java/com/ticketbox/api/infrastructure/config/JacksonConfig.java`
    - `backend/src/main/java/com/ticketbox/api/module/catalog/domain/dtos/UpdateConcertRequest.java`
    - `backend/src/main/java/com/ticketbox/api/module/catalog/domain/dtos/UpdateSeatZoneRequest.java`
    - `backend/src/main/java/com/ticketbox/api/module/catalog/domain/dtos/UpdateTicketTypeRequest.java`
    - `backend/src/main/java/com/ticketbox/api/module/catalog/services/ConcertServiceImpl.java`
    - `backend/src/test/java/com/ticketbox/api/infrastructure/config/JacksonConfigTest.java`
    - `backend/src/test/java/com/ticketbox/api/module/catalog/services/AdminConcertServiceTest.java`
    - `DESISIONS.md`
    - `.agents/desisions/02-json-nullable-partial-update.md`
    - `NoteMVC.md`
    - `PROGRESS.md`
  - Known risk or unresolved issue: Không có rủi ro nào.
  - Next best step: Trình bày chi tiết triển khai cho người dùng theo skill `implementation-explain` để tiến hành review và commit.