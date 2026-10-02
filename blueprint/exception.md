# Thiết kế BusinessException cho TicketBox

> Trạng thái: thiết kế gốc được inspect ngày 02/10/2026. Lát triển khai exception infrastructure và migrate exception catalog đã hoàn tất trong working tree hiện tại. AppException vẫn là adapter tạm cho các module chưa migrate; idempotency, frontend, Postman và rollout cache chưa thuộc lát này.

## 1. Mục tiêu và quyết định chính

Chuẩn hóa lỗi nghiệp vụ theo ba tầng exception, tách HTTP khỏi nghiệp vụ và thống nhất phản hồi lỗi của REST API. Áp dụng vào kiến trúc hiện tại `Controller → Service → Repository`, không yêu cầu tái cấu trúc toàn bộ project sang DDD.

- `BusinessException` là unchecked exception cho tình huống nghiệp vụ dự kiến; không lưu HTTP status, không có cause/stack trace.
- Base class nằm trong `com.ticketbox.api.module.shared.exception`; exception cụ thể và enum mã lỗi nằm trong `<module>.domain.exception`.
- Một handler cho `BusinessException`, chỉ phụ thuộc shared contract. `ErrorType` quyết định HTTP status tại tầng web.
- Dùng `ProblemDetail` cho REST API của ứng dụng. Validation đầu vào trả **400**, business trả **404/409/422** hoặc **401/403** cho auth/quyền; technical mặc định **500**. Giữ **503** cho dịch vụ phụ thuộc tạm thời không khả dụng và **429** cho rate limit.
- Giữ nguyên các chuỗi code đã dùng; áp dụng prefix module cho code mới. Không đổi `EMAIL_ALREADY_EXISTS` thành `AUTH_EMAIL_ALREADY_EXISTS` chỉ để đẹp tên.
- Callback VNPay/MoMo và OAuth2 redirect là các giao thức riêng; không ép mọi phản hồi vào `ProblemDetail`.
- Triển khai từng bước với adapter tương thích tạm thời cho `AppException`, rồi loại bỏ adapter sau khi hết nơi dùng và hết thời hạn record cache cũ.

Tiêu chí hoàn thành triển khai: service/domain không còn import `HttpStatus` để ném lỗi; HTTP status/code/schema có kiểm thử; lỗi nghiệp vụ vẫn giữ đúng nghĩa khi qua idempotency/cache; technical giữ nguyên nguyên nhân để tra log; frontend và filter đọc/ghi cùng contract.

## 2. Những gì đã xác nhận trong project

Các đường dẫn dưới đây tính từ repository root; đường dẫn Java rút gọn bắt đầu tại `backend/src/main/java/com/ticketbox/api/`.

| Vị trí | Hiện trạng | Hệ quả thiết kế |
| --- | --- | --- |
| `backend/pom.xml` | Java 21, Spring Boot 4.0.6, Web MVC, Validation, Security, Redis, JPA, AMQP | Dùng API Spring MVC tương ứng dependency đã resolve; không chép nguyên signature của ví dụ Boot 3 |
| `infrastructure/exception/AppException.java` | Mang `HttpStatus`, string code, `Object details`; stack trace bật | Đang trộn nghiệp vụ với HTTP, chưa ràng buộc code/type |
| `infrastructure/exception/GlobalExceptionHandler.java` | Chưa kế thừa `ResponseEntityExceptionHandler`; trả `ErrorResponse`; validation 422 | Cần xử lý lỗi Spring MVC và đổi validation sang 400 có migration |
| `module/catalog/controllers/AdminCatalogExceptionHandler.java` | Advice ưu tiên cao, áp dụng ba controller admin catalog, trả `ProblemDetail`; tự sinh `request_id` | Hợp nhất vào global advice; nếu giữ nguyên sẽ che handler mới |
| `infrastructure/response/ErrorResponse.java` | `{error:{code,message,details},meta:{request_id}}`; sinh ID ngẫu nhiên lúc tạo response | ID chưa gắn với vòng đời request/log |
| `infrastructure/response/AdminProblemWriter.java` | Ghi problem JSON dạng map cho `/admin/**` | Mở rộng thành writer chung cho filter, dùng chung factory với MVC |
| `infrastructure/config/SecurityConfig.java`, `infrastructure/rateLimit/RateLimitFilter.java` (fix after)| Tự ghi response trước controller; format admin và ngoài admin khác nhau | Advice không bao phủ các nhánh này; cần đổi writer trực tiếp |
| `module/shared/idempotency/IdempotencyService.java`, `IdempotencyHelper.java`, `IdempotencyRecord.java` | Bắt/cache/replay `AppException`; record lưu HTTP status; lỗi Redis dùng `IDEMPOTENCY_UNAVAILABLE` 503 | Phải migrate cùng exception; không cache lỗi kỹ thuật như business |
| `module/shared/cache/SingleFlight.java` | Nhánh chờ đã rethrow nguyên `RuntimeException` từ cause | Giữ hành vi để business không biến thành 500; thêm regression test |
| `module/payment/controllers/PaymentController.java` | VNPay bắt runtime và trả 200 với mã `99`; MoMo trả status theo `CallbackHandlingResult` | Giữ adapter giao thức payment; không xóa catch theo quy tắc máy móc |
| `module/payment/gateways/MomoGateway.java`, `module/payment/services/PaymentServiceImpl.java` | Có `CreateRejectedException`, `CreateOutcomeUnknownException` và xử lý reconciliation | Không thay các outcome này bằng business exception chung |
| Các entity `Order`, `Payment`, `TicketType`, `UserTicketTypeCounter` | Có kiểm tra trạng thái bằng `IllegalStateException`, một số trong lifecycle persistence | Phân biệt invariant do lệnh người dùng với dữ liệu hệ thống bất nhất |
| `frontend/src/lib/api-client.ts` | Parser đã đọc cả envelope cũ và problem JSON, có `ApiClientError.code` | Có nền tảng migration; cần bổ sung correlation/details khi cần |
| `frontend/src/routes/auth/AuthPage.tsx` | Switch trực tiếp các code `EMAIL_ALREADY_EXISTS`, `INVALID_OTP`, ... | Rename code là breaking change |
| `blueprint/api-design/base-api.md` | Đã mô tả problem JSON, `request_id`, `X-Request-Id`, snake_case | Cần đồng bộ spec với contract mới; không giả định blueprint đã được hiện thực đầy đủ |

Chưa tìm thấy cơ chế `MDC`/`traceId` trong source đã inspect. Không thể chỉ thêm `MDC.get("traceId")` vào handler rồi kỳ vọng có correlation. `ConcertServiceImpl.java` đang có thay đổi cục bộ trước task; tài liệu dựa trên working tree hiện tại và không chỉnh sửa file đó.

## 3. Phân loại lỗi và quyền sở hữu

| Loại | Ví dụ thực tế | Cách biểu diễn | HTTP và log |
| --- | --- | --- | --- |
| Validation đầu vào | JSON sai, UUID sai, thiếu header, `@Valid`, field không hỗ trợ | Exception MVC hoặc `RequestValidationException` độc lập | 400; không stack trace; DEBUG/INFO nếu cần |
| Business không tìm thấy | Không tìm thấy order mà client yêu cầu | `OrderNotFoundException` | 404, WARN không stack trace |
| Business xung đột | Hết vé, vượt quota, email trùng, order không còn HELD | Exception kế thừa `ConflictException` | 409, WARN không stack trace |
| Business rule | Ngoài thời gian bán, không thể publish concert | Exception kế thừa `BusinessRuleException` | 422, WARN không stack trace |
| Auth/quyền dự kiến | Sai credentials, token hết hạn, không sở hữu order | Business auth hoặc exception Spring Security ở boundary | 401/403; không stack trace |
| Technical | SQL lỗi, lỗi mã hóa, dữ liệu inventory bất nhất | Exception gốc hoặc technical wrapper giữ cause | 500, ERROR kèm stack trace, thông báo công khai chung |
| Dependency unavailable | Redis idempotency/rate limiter fail-closed | Technical exception có cause hoặc writer tại filter | 503, ERROR kèm cause nếu có |
| Rate limit | Vượt giới hạn request | Quyết định tại filter | 429, giữ `Retry-After`; log như policy hiện tại |

Không thêm `BAD_REQUEST`, `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` vào `ErrorType` của business. Tên interface `ErrorCode` ở đây chỉ dùng cho business; mã validation/protocol/technical thuộc catalog mã tại boundary tương ứng.

`RequestValidationException` mang code/message/errors nhưng không mang HTTP; đặt trong `module/shared/validation` để cả application service và HTTP adapter dùng được. Chỉ dùng cho kiểm tra cấu trúc input không phụ thuộc DB (ví dụ danh sách chứa ticket type trùng, password confirmation). Không biến mọi `IllegalArgumentException` hoặc `ConstraintViolationException` thành 400: chúng cũng có thể là bug, validation entity hoặc lỗi return value.

## 4. Shared kernel và phân cấp ba tầng

Cấu trúc dự kiến:

```text
module/shared/exception/
  ErrorType.java
  ErrorCode.java
  BusinessException.java                 (abstract)
  NotFoundException.java                 (abstract)
  ConflictException.java                 (abstract)
  BusinessRuleException.java             (abstract)
  ForbiddenException.java                (abstract)
  UnauthorizedException.java             (abstract)
module/shared/validation/
  RequestValidationException.java
module/order/domain/exception/
  OrderErrorCode.java
  OrderNotFoundException.java
  OrderAccessDeniedException.java
  OrderNotPayableException.java
  ...                                    (chỉ tạo khi có use case)
module/auth/domain/exception/
  AuthErrorCode.java
  EmailAlreadyUsedException.java
module/catalog/domain/exception/
  CatalogErrorCode.java
  ConcertNotFoundException.java
  ...
module/payment/domain/exception/
  PaymentErrorCode.java
  ...
module/ticket/domain/exception/
  TicketErrorCode.java
  ...
```

Mỗi exception tầng 2 cố định một `ErrorType`, nhưng `ErrorCode.type()` cũng chứa type. Chọn **enum code làm nguồn dữ liệu duy nhất**, constructor tầng 2 kiểm tra tính nhất quán để không tồn tại `NotFoundException` mang code `CONFLICT`.

Mẫu Java minh họa contract (tách thành các file đúng package khi triển khai):

```java
public enum ErrorType {
    NOT_FOUND, CONFLICT, UNPROCESSABLE, FORBIDDEN, UNAUTHORIZED
}

public interface ErrorCode {
    String code();
    ErrorType type();
}

public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    protected BusinessException(ErrorCode code, ErrorType expectedType,
                                String message, Map<String, Object> details) {
        super(Objects.requireNonNull(message), null, false, false);
        this.errorCode = Objects.requireNonNull(code);
        if (code.code() == null || code.code().isBlank()
                || code.type() != Objects.requireNonNull(expectedType)) {
            throw new IllegalArgumentException("Invalid business error definition");
        }
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public final ErrorCode getErrorCode() { return errorCode; }
    public final Map<String, Object> getDetails() { return details; }
}

public abstract class NotFoundException extends BusinessException {
    protected NotFoundException(ErrorCode code, String message,
                                Map<String, Object> details) {
        super(code, ErrorType.NOT_FOUND, message, details);
    }
}

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(ErrorType.NOT_FOUND),
    ORDER_ACCESS_DENIED(ErrorType.FORBIDDEN),
    ORDER_NOT_PAYABLE(ErrorType.CONFLICT);

    private final ErrorType type;
    OrderErrorCode(ErrorType type) { this.type = type; }
    public String code() { return name(); }
    public ErrorType type() { return type; }
}

public final class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(UUID orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found",
              Map.of("order_id", orderId.toString()));
    }
}
```

Các lớp tầng 2 còn lại có cùng pattern, cố định type tương ứng. `IllegalArgumentException` trong constructor là lỗi lập trình, giữ stack trace; không trả thành validation 400. Dùng `final` cho tầng 3, không dùng `@Data` cho exception.

Quy tắc `details`:

- Chỉ gồm dữ liệu công khai, nhỏ, JSON-safe và do code kiểm soát: ID được phép thấy, giới hạn, trạng thái công khai. Không chứa entity, SQL, token, OTP, password, email đầy đủ hoặc payload nhà cung cấp.
- `Map.copyOf` chỉ immutable ở cấp map, từ chối key/value null. Chỉ dùng scalar immutable hoặc collection đã copy; không gọi đây là deep copy.
- ID trong details dùng string để lần ném đầu và lần replay cache có cùng JSON representation. `details` mặc định `{}`.
- Exception không giữ request, HTTP status hay `traceId`. Correlation thuộc mỗi lần HTTP request, không thuộc business outcome.

## 5. Danh mục mã lỗi và migration từ AppException

Giữ wire code cũ trong enum sở hữu nghiệp vụ. Code mới dùng `AUTH_*`, `CATALOG_*`, `ORDER_*`, `PAYMENT_*`, `TICKET_*`; không ép prefix mới lên code cũ. Một code chỉ có một owner/type, kể cả được dùng từ nhiều service.

| Owner / code đang có | Hiện tại → đích | Exception hoặc nhóm xử lý đề xuất |
| --- | --- | --- |
| auth: `EMAIL_ALREADY_EXISTS`, `PHONE_ALREADY_EXISTS` | 409 → 409 | `EmailAlreadyUsedException`, `PhoneAlreadyUsedException` / CONFLICT |
| auth: `USER_NOT_FOUND` | 404 → 404 | `UserNotFoundException` |
| auth: `INVALID_CREDENTIALS`, `ACCOUNT_NOT_VERIFIED`, `ACCOUNT_DISABLED`, `TOKEN_EXPIRED`, `TOKEN_REVOKED` | 401 → 401 | Exception auth / UNAUTHORIZED; message credentials không phân biệt email tồn tại |
| auth: `PASSWORD_MISMATCH` | 400 → 400 | Request validation, không thuộc BusinessException |
| auth: `INVALID_OTP` | 400 → **422** | `InvalidOtpException` / UNPROCESSABLE; kiểm tra trạng thái OTP là business |
| auth: `ALREADY_VERIFIED` | 400 → **409** | `AccountAlreadyVerifiedException` / CONFLICT |
| catalog: `CONCERT_NOT_FOUND`, `SEAT_ZONE_NOT_FOUND`, `TICKET_TYPE_NOT_FOUND` | 404 → 404 | Exception NOT_FOUND của catalog; order có thể dùng lại |
| catalog: `SLUG_ALREADY_EXISTS`, `SEAT_ZONE_CODE_ALREADY_EXISTS`, `TICKET_TYPE_NAME_ALREADY_EXISTS`, `INVALID_CONCERT_STATE` | 409 → 409 | Exception CONFLICT của catalog |
| catalog: `CANNOT_PUBLISH_CONCERT`, `INVALID_CONCERT_TIME_RANGE`, `INVALID_QUANTITY`, `INVALID_SALE_WINDOW`, `ZONE_CAPACITY_EXCEEDED` | 422 → 422 | Business rule; giữ kiểm tra sớm tại service trước persistence |
| catalog: `FORBIDDEN` | 403 → 403 | `ConcertAccessDeniedException`; giữ code legacy, code mới nên là `CATALOG_ACCESS_DENIED` |
| catalog/input: `INVALID_STATUS`, `INVALID_REQUEST_FIELD` | 400 → 400 | Request validation |
| input: `VALIDATION_ERROR` | 422 → **400** | Validation input/enum/filter; không dùng code này cho business rule mới |
| order: `ORDER_NOT_FOUND`, `ORDER_ACCESS_DENIED` | 404/403 → giữ nguyên | Exception order, dùng lại trong payment |
| order: `TICKET_SOLD_OUT`, `PER_USER_LIMIT_EXCEEDED`, `ORDER_NOT_SETTLABLE` | 409 → 409 | CONFLICT; inventory/quota hiện thuộc order, không tạo module inventory mới |
| order: `TICKET_TYPE_NOT_ON_SALE`, `SALE_WINDOW_CLOSED` | 422 → 422 | Business rule tại use case đặt vé |
| order/input: `INVALID_CHECKOUT_REQUEST` khi ticket type lặp | 400 → 400 | Request validation |
| order: `INVALID_CHECKOUT_REQUEST` khi khác concert/currency | 400 → **422** | BusinessRuleException; code hiện bị dùng cho hai ý nghĩa, xem quy tắc tách bên dưới |
| order: `ORDER_NOT_PAYABLE`, `ORDER_HOLD_EXPIRED`, `ORDER_ALREADY_PAID` | 409 → 409 | OrderErrorCode dù hiện được ném tại payment service |
| payment: `PAYMENT_NOT_FOUND`, `PAYMENT_IN_PROGRESS` | 404/409 → giữ nguyên | Exception payment |
| payment: `PAYMENT_PROVIDER_UNAVAILABLE` khi không có strategy cho provider | 422 → 422 | Business rule nếu provider hợp lệ nhưng không được hỗ trợ; thiếu strategy do cấu hình sai phải là technical |
| ticket: `TICKET_NOT_FOUND`, `TICKET_QR_UNAVAILABLE` | 404/409 → giữ nguyên | Exception ticket |
| shared idempotency: `IDEMPOTENCY_KEY_REUSED` | 400 → **409** | Shared conflict, cùng định nghĩa cho helper/order/payment |
| input: `MISSING_IDEMPOTENCY_KEY`, `INVALID_IDEMPOTENCY_KEY` | 400 → 400 | Header validation tại HTTP boundary |
| technical: `IDEMPOTENCY_UNAVAILABLE` | 503 → 503 | Technical exception, giữ cause; không kế thừa BusinessException |
| web/security: `UNAUTHORIZED`, `FORBIDDEN`, `TOKEN_REVOKED` | 401/403 → giữ nguyên | Writer tại security boundary; không cần biến thành business exception |
| web/rate limit: `RATE_LIMITED`, `RATE_LIMIT_UNAVAILABLE` | 429/503 → giữ nguyên | Writer tại filter, giữ header/policy |
| technical fallback: `INTERNAL_SERVER_ERROR` | 500 → 500 | Giữ code đang dùng thay vì đổi thành `INTERNAL_ERROR` |

Các số in đậm là **thay đổi contract có chủ đích**, cần cập nhật test/spec và phát hành cùng migration. Đây là đích thiết kế, không được lặng lẽ thay status trong bước chỉ thêm base exception.

Riêng `INVALID_CHECKOUT_REQUEST`: ở bước tương thích giữ code/status cũ; ở bước cutover đổi code của hai rule cần dữ liệu DB thành `ORDER_TICKET_CONCERT_MISMATCH` và `ORDER_CURRENCY_MISMATCH` (422), giữ `INVALID_CHECKOUT_REQUEST` (400) cho input trùng lặp. Frontend hỗ trợ code mới trước backend. Không để một business code mang hai type. Rà soát từng call site `validation(...)` trong catalog; chỉ chuyển kiểm tra input sang 400, các rule đã có mã riêng tiếp tục 422.

Ownership là theo quy tắc, không theo file đang throw: payment được dùng `OrderNotFoundException` của order; global handler không import exception đó. Shared idempotency sở hữu exception key reused vì logic này dùng xuyên module. Nếu module cần che sự tồn tại của resource, service quyết định 404; không đưa logic quyền vào handler.

## 6. Luồng ném và transaction

```text
Request → correlation filter → security/rate limit → controller
                                                      ↓
                                              service / domain
                                                      ↓ throws
                                             BusinessException
                                                      ↓
                              GlobalExceptionHandler → ProblemDetail
```

Service tìm entity bằng `orElseThrow(() -> new OrderNotFoundException(id))`. Controller gọi service rồi trả DTO; không catch business để tự xây error body.

Entity chỉ throw business tại hành vi domain có ý nghĩa và đầu vào gây vi phạm quy tắc dự kiến. Hiện project chưa có đầy đủ các method hành vi kiểu `order.cancel()`; không thêm use case hủy đơn chỉ để minh họa hierarchy. Giữ `IllegalStateException` cho các tình huống như held inventory không khớp order đã thanh toán, thiếu timestamp bắt buộc do bug, lỗi crypto/config. Không đổi toàn bộ guard `@PrePersist`/`@PreUpdate` thành BusinessException vì có thể che corruption và exception còn bị persistence wrapper bọc lại.

Unchecked exception rollback mặc định khi thoát qua transaction boundary được Spring quản lý, bao gồm đường dùng `TransactionTemplate`; bị catch/swallow thì không thể giả định rollback. Không thêm `noRollbackFor` đại trà. Với unique constraint, chỉ chuyển đúng constraint đã biết thành conflict; race phải được xác minh cả lúc flush/commit. Nếu transaction đã bị đánh dấu rollback-only, thoát transaction thay vì tiếp tục ghi dữ liệu trong catch.

Lỗi kỹ thuật bọc thêm context phải giữ cause. Service không vừa log ERROR vừa rethrow để global handler log thêm; log tại boundary chịu trách nhiệm cuối cùng. Worker/scheduler có boundary riêng, không nhận xử lý từ MVC advice.

## 7. Contract phản hồi HTTP

Đích cho REST API ứng dụng: `Content-Type: application/problem+json`, HTTP status bằng `status` trong body. Giữ response thành công hiện tại.

```json
{
  "type": "https://api.ticketbox.vn/errors/order-not-found",
  "title": "ORDER_NOT_FOUND",
  "status": 404,
  "detail": "Order not found",
  "instance": "/orders/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "code": "ORDER_NOT_FOUND",
  "details": {"order_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"},
  "traceId": "req_bf46a3e1687c4dc5b6c8a1ae9b01d942",
  "request_id": "req_bf46a3e1687c4dc5b6c8a1ae9b01d942"
}
```

- `code` là contract xử lý; `detail` dành cho con người, có thể đổi/ngôn ngữ hóa. `title` bằng code để nối tiếp admin contract hiện có.
- Giữ quy ước type URI hiện có của admin; đây là định danh, chưa khẳng định URL đó đang host trang tài liệu.
- `instance` chỉ là request path, không query string chứa token/PII.
- `traceId` theo yêu cầu thiết kế; giữ `request_id` như alias tương thích với blueprint/admin. Hai field và `X-Request-Id` dùng cùng một giá trị. Đây là request correlation ID, chưa phải distributed tracing ID.
- JSON extension phải ở top level, không lồng trong `properties`. `details` luôn object; `errors` chỉ xuất hiện khi validation có danh sách lỗi.
- Validation: `code=VALIDATION_ERROR`, 400, `errors=[{"field":"items[0].quantity","code":"Min","message":"must be at least 1"}]`. Object-level errors có `field=null`. Không ép mọi `ObjectError` thành `FieldError` như handler hiện tại, không làm mất nhiều lỗi trên một field, không trả rejected value.
- Technical 500: `code=INTERNAL_SERVER_ERROR`, `detail="An unexpected server error occurred"`, `details={}`. Tuyệt đối không lấy `ex.getMessage()` cho phản hồi 500.

Correlation filter mới đặt trước security chain, sinh UUID đầy đủ cho mỗi request, lưu request attribute và MDC key `traceId`, đặt response header, dọn/khôi phục MDC trong `finally`. Mặc định ID do server sinh; nếu muốn nhận `X-Request-Id` từ proxy phải có allowlist độ dài/ký tự và trust boundary rõ ràng. Không dùng dữ liệu header thô trong log. Giữ ID qua ERROR/ASYNC dispatch bằng request attribute và bind lại MDC mỗi dispatch; chỉ đăng ký filter một lần. CORS cần expose `X-Request-Id` và `Retry-After` cho frontend. Request thành công cũng trả header này; không mở rộng task sang sửa toàn bộ success envelope.

## 8. Handler và writer chung

Giữ vị trí `infrastructure/exception/GlobalExceptionHandler.java`, đổi sang `extends ResponseEntityExceptionHandler`. Tạo `BusinessHttpStatusMapper`, `ApiProblemFactory` và `ApiProblemWriter` ở infrastructure. Handler và filter dùng cùng factory, tránh hai cách sinh ID/format.

Business mapper chỉ biết enum:

```java
return switch (type) {
    case NOT_FOUND -> HttpStatus.NOT_FOUND;
    case CONFLICT -> HttpStatus.CONFLICT;
    case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
    case FORBIDDEN -> HttpStatus.FORBIDDEN;
    case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
};
```

Handler có đúng một `@ExceptionHandler(BusinessException.class)`: lấy code/type, log WARN với code + correlation, tạo problem từ public message/details. Không truyền exception vào logger ở nhánh này. `@ExceptionHandler(Exception.class)` log ERROR kèm exception, trả 500 chung. Giữ mapping riêng cho Spring Security exceptions tại MVC/method security để không bị generic handler chuyển thành 500.

| Nhánh | Điểm xử lý | Đích |
| --- | --- | --- |
| Body bean validation | Override `handleMethodArgumentNotValid` | 400 + `errors` gồm field và object errors |
| MVC method validation | Override `handleHandlerMethodValidationException` | Input 400; return-value violation 500 với message chung |
| JSON/enum/date deserialize lỗi | Override `handleHttpMessageNotReadable` | 400, message công khai; không trả parser/Java class details |
| UUID/query/path sai kiểu | Override `handleTypeMismatch` | 400 |
| Header/query bắt buộc thiếu | Hook của superclass hoặc common hook | 400, code cụ thể khi đã có contract |
| Sai method/media type/Accept | Giữ handling superclass, bổ sung common properties | 405/415/406; không làm mất `Allow` và các header framework |
| Route/resource không có | Handling superclass cho no-handler/no-resource | 404 với code web riêng `ROUTE_NOT_FOUND` |
| RequestValidationException | Handler riêng, không phải subclass business | 400, code/details đã chuẩn hóa |
| Unexpected và lỗi framework 5xx | Generic handler và common hook | Mask message, log stack trace tại một boundary |

Không khai báo thêm `@ExceptionHandler(MethodArgumentNotValidException.class)` khi đã kế thừa superclass. Common customization đi qua `handleExceptionInternal`/`createResponseEntity` theo signature dependency thực tế: bổ sung code/correlation cho body framework, giữ status/header, tôn trọng response đã committed (không ghi lần hai). Không chỉ mask trong generic handler: return-value validation và lỗi write response có thể đi qua superclass.

`@Validated` proxy method validation có thể tạo `ConstraintViolationException`; chỉ map input tại controller boundary sau khi xác định nguồn. Service/return-value/persistence violations không mặc định là client error. Không unwrap tùy ý mọi root cause thành business: có thể đánh mất trạng thái transaction và phân loại kỹ thuật.

`UnknownAdminField` hiện throw `AppException` trong Jackson deserialization. Chuyển sang lỗi request có mã `INVALID_REQUEST_FIELD`; handler malformed có thể nhận diện đúng marker này trong cause chain hữu hạn, không cho phép nâng một exception bất kỳ thành public response.

Filter không được giả định advice sẽ chạy. `AuthenticationEntryPoint`, `AccessDeniedHandler`, `RateLimitFilter` gọi `ApiProblemWriter` và return, giữ nguyên status/header và auth policy. Rà `JwtFilter` để lỗi kỹ thuật khi load user/Redis không bị nuốt hoặc ghi mất stack trace; invalid token dự kiến vẫn xử lý theo auth contract. Không catch toàn bộ downstream chain để ghi một lỗi thứ hai khi response đã commit.

Writer có thể tiếp tục serialize một map whitelist như `AdminProblemWriter`, dùng dữ liệu factory. Project hiện có Jackson `com.fasterxml` tại filter/cache; phải kiểm tra serialization thực tế khi trộn với MVC mapper trong Boot 4. Không tự `new ObjectMapper()` rồi mặc định rằng mixin flatten `ProblemDetail.properties` đã được đăng ký. Task này không cần nâng cấp toàn bộ Jackson.

Spring hỗ trợ `ProblemDetail` mở rộng và superclass để chuẩn hóa lỗi MVC; đây là nền tảng cho thiết kế boundary trên. Tham khảo [Spring MVC Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) và [ResponseEntityExceptionHandler 7.0.9](https://docs.spring.io/spring-framework/docs/7.0.9/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html). Khi triển khai phải đối chiếu phiên bản Maven resolve của project, không dùng `current` làm giả định version.

## 9. Idempotency, cache và các boundary đặc biệt

### 9.1. Idempotency bắt buộc migrate cùng business

Nếu chỉ đổi `throw new AppException` thành `BusinessException`, `IdempotencyService` sẽ rơi vào catch `RuntimeException`, xóa processing marker và mất replay lỗi nghiệp vụ. Ngược lại, technical 503 hiện nằm trong AppException có nguy cơ đi vào nhánh lưu lỗi hoàn tất.

Thiết kế đích:

1. Bắt `BusinessException` để lưu outcome business đã kết thúc; giữ compare-and-set hiện tại. Input validation chạy trước acquire marker khi có thể.
2. Lỗi technical không lưu như outcome business; giữ cause và cleanup marker theo ownership hiện tại. Redis lỗi khi lưu/cleanup không được làm mất lỗi gốc: log lỗi phụ cùng trace; nếu commit DB đã thành công thì lần retry phải phục hồi từ DB trước khi thực hiện side effect.
3. Record mới có `schemaVersion`, `state`, `fingerprint`, `outcome`; error snapshot có `code`, `type`, `message`, `details`, **không có HttpStatus/traceId**. Thành công giữ response payload. Không serialize Java exception/class name.
4. Replay tạo `ReplayedBusinessException` nội bộ shared idempotency từ `StoredErrorCode(code,type)` immutable; constructor vẫn kiểm tra type. Đây là adapter replay, không phải API cho service tự tạo lỗi tùy ý, không cần import enum từng module.
5. Chỉ chấp nhận record đúng schema và type/code hợp lệ. Record hỏng/không tương thích là technical unavailable có log, không tự thực hiện lại action.
6. Request replay nhận traceId mới từ request hiện tại; JSON code/type/details phải tương đương lần ném đầu.
7. `IDEMPOTENCY_KEY_REUSED` dùng một shared ConflictException thay cho các bản lặp ở helper/order/payment; retry cùng key khác payload không được chạy action. Lỗi `IDEMPOTENCY_UNAVAILABLE` là technical 503; timeout chờ có thể không có cause, nhưng stack trace vẫn bật.

Không đổi key namespace hay flush toàn bộ Redis để né record cũ: có thể mất marker đang xử lý và tạo trùng side effect. Thêm dual reader trước khi bật new writer; decoder legacy có HTTP mapping nằm trong `infrastructure/idempotency`, không đưa HTTP trở lại shared kernel. Nó chuyển các record lỗi cũ theo danh mục đã biết sang business/validation/technical snapshot trung lập. Record 400 validation cũ không được cưỡng ép thành business; 503 cũ không được coi là business. Business adapter tạm tại web giữ status cũ trong giai đoạn tương thích.

Rollout: triển khai reader tương thích trên mọi instance, sau đó bật writer schema mới, chờ hết TTL lớn nhất của record cũ cộng thời gian request đang chạy rồi bỏ decoder. Không cho instance chỉ đọc schema cũ chạy cùng new writer. Chốt TTL thực tế từ cấu hình trước release. Chuyển status/code công khai ở bước cutover riêng, không gắn ngầm với phiên bản record. Không mở rộng thay đổi này thành thiết kế lại durable idempotency; tiếp tục giữ DB lookup/constraint và reconciliation hiện có.

### 9.2. SingleFlight, callback, worker

- `SingleFlight`: giữ nguyên rethrow cause runtime ở nhánh chờ. Test cả request thực thi supplier và request join đều nhận business gốc.
- VNPay IPN: giữ response 200 + `PaymentCallbackResponse`, gồm fallback mã `99` khi có lỗi xử lý. Catch này là adapter protocol có chủ đích; giữ log technical tại đó.
- MoMo IPN: giữ mapping `CallbackHandlingResult` hiện có (204/400/404/422/409). Lỗi parse/validation trước method và lỗi technical vẫn cần contract được kiểm thử riêng; không hứa mọi lỗi MoMo có body rỗng vì hiện advice còn xử lý trước controller.
- `CreateOutcomeUnknownException`: giữ cause và đường reconciliation, không đồng nhất timeout với thanh toán thất bại; không sinh business 422 từ mọi lỗi cổng thanh toán.
- Consumer/worker/scheduler: tự phân loại retry/DLQ theo operation. Không tự động ACK mọi BusinessException hay retry vô hạn vì không còn stack trace; dữ liệu thiếu ở một event đã thanh toán có thể là technical cần điều tra.
- OAuth2 success/failure redirect: giữ giao thức redirect hiện tại, không đổi sang JSON chỉ vì hợp nhất error writer.

## 10. Kế hoạch triển khai theo bước

### Bước 1 — Chốt contract và shared model

Tạo base classes và enum/exception mẫu của order; test code/type, details, stack trace. Lập test contract cho schema đích và bảng thay đổi status/code. Chưa đổi status hay shape endpoint đang chạy.

### Bước 2 — Correlation và web infrastructure

Tạo filter/factory/writer/mapper; nâng global advice. Trong giai đoạn chuyển đổi giữ handler `AppException` legacy, không để BusinessException kế thừa AppException. Adapter có thể render format cũ theo phạm vi endpoint cần tương thích; đó là trách nhiệm web, không đưa flag legacy vào domain.

Hợp nhất logic `AdminCatalogExceptionHandler` trong cùng bước thay advice; gỡ advice ưu tiên cao khi contract tương đương đã có test. Chuyển security/rate limit sang writer chung theo cùng lịch cutover format, giữ mã/headers.

### Bước 3 — Idempotency compatibility

Triển khai dual reader/writer có phiên bản và phân biệt business/technical trước khi migrate order/payment. Trong thời gian còn AppException, chỉ cache legacy lỗi nghiệp vụ đã phân loại; không catch mọi AppException rồi cache 503. Test replay record cũ và mới, commit thành công nhưng Redis lỗi, request chờ trùng key.

### Bước 4 — Migrate module theo lát nhỏ

Catalog → auth → order → payment → ticket. Mỗi lát thay AppException theo danh mục, bỏ import HTTP không còn dùng ở service, cập nhật service/controller tests. Dùng lại exception resource của module owner; không nhân bản enum theo caller. Kiểm tra catalog catch unique constraint, auth unique race và payment transaction/reconciliation sau mỗi lát có ảnh hưởng.

### Bước 5 — Cutover API contract

Frontend dual parser đã có nhưng vẫn kiểm tra error messages, field errors, correlation và các status đổi. Thêm code checkout mới trước backend. Cập nhật blueprint/Postman, sau đó bật ProblemDetail cho toàn bộ REST API ứng dụng và validation 400 cùng các status đổi trong bảng. Nếu có client bên ngoài chưa xác minh được tương thích, giữ adapter cho phạm vi đó tới release đã thông báo; không mặc định frontend trong repo là client duy nhất.

### Bước 6 — Dọn legacy

Sau khi mọi caller và record cũ đã migrate: xóa AppException, ErrorResponse/writer cũ và handler tương thích nếu không còn import; bỏ reader cũ sau TTL. Cập nhật quy ước lỗi trong `AGENT.md`. Kiểm tra không còn raw string business codes rải trong service, không có HTTP import trong shared exception/module exception và không còn nhiều handler cạnh tranh.

## 11. Tệp cần xem/chỉnh sửa khi triển khai

Quy ước: `J = backend/src/main/java/com/ticketbox/api`, `T = backend/src/test/java/com/ticketbox/api`. Các file mới là đề xuất; đợt viết tài liệu này chỉ chỉnh `blueprint/exception.md`.

| Nhóm | Đường dẫn | Trách nhiệm |
| --- | --- | --- |
| Đã xem | `backend/pom.xml`, `AGENT.md`, `blueprint/api-design/base-api.md` | Stack, quy ước hiện tại, compatibility |
| Tạo mới | `J/module/shared/exception/{ErrorType,ErrorCode,BusinessException,NotFoundException,ConflictException,BusinessRuleException,ForbiddenException,UnauthorizedException}.java` | Shared hierarchy |
| Tạo mới | `J/module/shared/validation/RequestValidationException.java` | Lỗi input trung lập HTTP |
| Tạo mới | `J/module/{auth,catalog,order,payment,ticket}/domain/exception/` | Enum và exception theo danh mục; không tạo module chưa dùng |
| Tạo mới | `J/infrastructure/exception/BusinessHttpStatusMapper.java` | Map type → HTTP tại web |
| Tạo mới | `J/infrastructure/response/{ApiProblemFactory,ApiProblemWriter}.java` | Dùng chung contract/correlation cho MVC và filter |
| Tạo mới | `J/infrastructure/filter/RequestCorrelationFilter.java` | ID một lần mỗi request, MDC cleanup, header |
| Chỉnh sửa | `J/infrastructure/exception/GlobalExceptionHandler.java` | Kế thừa superclass, business/validation/security/technical handling |
| Chỉnh sửa rồi gỡ | `J/infrastructure/exception/AppException.java`, `J/infrastructure/response/{ErrorResponse,AdminProblemWriter}.java`, `J/module/catalog/controllers/AdminCatalogExceptionHandler.java` | Tương thích, sau đó loại bỏ khi không còn dùng |
| Chỉnh sửa | `J/infrastructure/config/SecurityConfig.java`, `J/infrastructure/security/JwtFilter.java`, `J/infrastructure/rateLimit/RateLimitFilter.java` | Writer, correlation order, CORS, không nuốt technical |
| Chỉnh sửa | `J/module/shared/idempotency/{IdempotencyService,IdempotencyHelper,IdempotencyRecord}.java` | Catch/replay đúng nghĩa, schema version, loại HTTP khỏi record đích |
| Tạo mới | `J/module/shared/idempotency/{IdempotencyErrorCode,IdempotencyKeyReusedException,ReplayedBusinessException,StoredErrorCode,IdempotencyUnavailableException}.java` | Business conflict, replay và technical riêng |
| Tạo mới, tạm thời | `J/infrastructure/idempotency/LegacyIdempotencyDecoder.java` | Adapter chuyển record cũ; shared chỉ phụ thuộc decoder contract trung lập |
| Chỉnh sửa | `J/module/auth/services/{UserServiceImpl,AdminUserServiceImpl}.java` | Auth exception/validation |
| Chỉnh sửa | `J/module/catalog/services/{ConcertServiceImpl,CatalogServiceImpl}.java`, `J/module/catalog/domain/dtos/UnknownAdminField.java` | Catalog business/validation, malformed input marker |
| Chỉnh sửa | `J/module/order/services/{OrderServiceImpl,OrderInventoryServiceImpl}.java`, `J/module/order/controllers/OrderController.java` | Order, quota, header validation |
| Chỉnh sửa | `J/module/payment/services/PaymentServiceImpl.java`, `J/module/payment/controllers/PaymentController.java` | Business/header validation, giữ callback protocol |
| Chỉnh sửa | `J/module/ticket/services/TicketServiceImpl.java` | Ticket exception |
| Có thể ảnh hưởng | `J/module/*/domain/entities/`, `J/module/shared/cache/SingleFlight.java`, consumers/schedulers | Chỉ đổi khi phân loại call site chứng minh cần; không blanket replace |
| Chỉnh sửa khi cutover | `frontend/src/lib/api-client.ts`, `frontend/src/routes/auth/AuthPage.tsx` | Expose trace/details khi cần, hỗ trợ mã và input errors |
| Có thể ảnh hưởng | `frontend/src/routes/audience/CheckoutPage.tsx`, các service/frontend caller khác | Rà xử lý code/status checkout mới |
| Chỉnh sửa spec | `blueprint/api-design/{base-api,auth-rbac-api,catalog-api,order-checkout-api,e-ticket-api}.md`, `AGENT.md` | Contract đích, bảng status, coding convention |
| Có thể ảnh hưởng | `postman/auth/auth.json`, `postman/order/order.json`, `postman/catalog/public/public.json`, request YAML auth | Assertion lỗi/formats |
| Chỉnh sửa test | `T/module/auth/services/UserServiceTest.java`, `T/module/catalog/services/{AdminConcertServiceTest,PublicConcertServiceTest}.java`, `T/module/order/services/{OrderServiceImplTest,OrderSettlementServiceImplTest}.java`, `T/module/payment/services/{PaymentServiceImplTest,PaymentResilienceServiceTest}.java`, `T/module/shared/idempotency/IdempotencyServiceTest.java` | Exception, type, replay, rollback/resilience |
| Chỉnh sửa test | `T/module/{auth,order,payment}/controllers/`, `T/infrastructure/rateLimit/RateLimitFilterTest.java` | Schema/status/header và protocol regression |
| Chỉnh sửa test | `T/module/shared/cache/SingleFlightTest.java` | Bổ sung kiểm tra business exception cho supplier và waiter |
| Tạo mới (tên dự kiến) | `T/infrastructure/exception/GlobalExceptionHandlerTest.java`, `T/infrastructure/security/SecurityProblemResponseTest.java`, `T/module/shared/exception/BusinessExceptionTest.java` | Test boundary và các regression còn thiếu |

## 12. Kế hoạch xác minh và điều kiện nghiệm thu

1. **Shared contract:** code không rỗng/type khớp; stack trace business rỗng; technical có cause/stack; details không sửa được từ map gốc; không import Spring HTTP. Registry test tập hợp enum của các module để phát hiện trùng wire code, không dựa vào convention prefix đơn thuần.
2. **MVC:** business từng type; generic 500; validation body/field/object; JSON/enum/date lỗi; UUID/query/header sai; 405 + Allow, 415, 406, route 404. Assert content type, code, details, correlation và không rò SQL/exception class. Thêm return-value validation 500 và framework 5xx để kiểm tra mask ngoài generic handler.
3. **Security/filter:** anonymous 401, method/URL forbidden 403, JWT revoked, rate limit 429 + Retry-After, Redis fail-closed 503. Cùng schema với MVC, không ghi response hai lần; ID trong header/body/log khớp, MDC không rò sang request tiếp theo, filter không chạy hai lần.
4. **Business/transaction:** không tìm thấy order/concert; email trùng; hết vé/vượt quota; order hết hạn; DB unique race chỉ map constraint đã biết. Business thoát transaction làm rollback inventory/order, không nuốt exception để vô tình commit.
5. **Idempotency:** lần đầu/replay cùng code/type/details; trace mới; fingerprint khác không chạy action; business được cache; technical không thành cached business; marker thuộc request khác không bị xóa; record cũ/new đều đọc; retry sau DB commit + Redis write lỗi không tạo order/payment thứ hai.
6. **Concurrency/cache:** supplier và waiter SingleFlight đều nhận BusinessException gốc; không thành RuntimeException generic 500.
7. **Payment:** giữ toàn bộ test callback status/body, duplicate callback, unknown outcome và reconciliation; không coi timeout là business thất bại chắc chắn.
8. **Frontend:** kiểm tra cả envelope legacy/problem JSON, mã auth hiện có, code checkout mới, field errors, 401 refresh behavior, 409/422 và trace hiển thị khi hỗ trợ. Project chưa có frontend test script; dùng build và kiểm tra UI có kịch bản, không ghi nhận build là đã test hành vi.

Lệnh có sẵn, chạy trong giai đoạn triển khai:

```bash
cd backend
./mvnw test
```

```bash
cd frontend
npm run build
```

Có thể chạy nhóm test liên quan từng lát trước full backend suite. Các kiểm tra DB race cần PostgreSQL thích hợp, không lấy H2 làm bằng chứng đủ cho hành vi constraint/locking PostgreSQL. Đợt viết tài liệu này chỉ xác minh nội dung/path và diff; chưa chạy test/build vì chưa đổi application code.
