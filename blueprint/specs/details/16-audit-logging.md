# Đặc tả: Audit Logging (Audit Trail Hệ Thống)

## 1. Mục tiêu và Nguyên tắc Cốt lõi

Bảng `audit_logs` là **audit trail** chính thức phục vụ Quản trị viên (Admin) điều tra sự cố bảo mật, truy vết vi phạm an toàn thông tin và đối soát thay đổi nghiệp vụ quan trọng.

Hệ thống tuân thủ nghiêm ngặt 3 nguyên tắc nền tảng:
1. **Độ tin cậy tuyệt đối (Reliability):** Audit trail phải phản ánh chính xác 100% thực tế diễn ra. Tuyệt đối không ghi nhận sự kiện không xảy ra và không để sót bất kỳ thay đổi nghiệp vụ thành công nào ("không có thay đổi vô hình").
2. **Bất biến (Immutability):** Nhật ký audit chỉ được thêm mới (INSERT) và đọc (SELECT). Nghiêm cấm chỉnh sửa (UPDATE) hoặc xóa (DELETE) ở cả tầng ứng dụng lẫn API.
3. **Bảo vệ dữ liệu nhạy cảm (Privacy & Security):** Tuyệt đối không lưu mật khẩu plaintext, hash mật khẩu, secret token, mã OTP, số thẻ thanh toán hay PII đầy đủ. Dữ liệu nhận diện chỉ lưu ID hoặc giá trị đã được che (masking).

---

## 2. Hai Loại Sự Kiện và Hai Cách Ghi

Hệ thống phân định rạch ròi mọi sự kiện audit thành 2 nhóm độc lập với cơ chế Transaction Propagation chuyên biệt:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SỰ KIỆN AUDIT                                   │
└───────────────────────┬─────────────────────────────┬───────────────────────┘
                        │                             │
                        ▼                             ▼
        ┌───────────────────────────────┐ ┌───────────────────────────────────┐
        │ Nhóm 1: Nghiệp vụ thành công  │ │ Nhóm 2: Sự kiện thất bại điều tra │
        ├───────────────────────────────┤ ├───────────────────────────────────┤
        │ • Transaction: REQUIRED       │ │ • Transaction: REQUIRES_NEW       │
        │ • Cùng transaction nghiệp vụ  │ │ • Transaction độc lập hoàn toàn   │
        │ • Commit / Rollback cùng nhau │ │ • Tồn tại kể cả cha rollback      │
        │ • Lỗi: KHÔNG NUỐT (Rollback)  │ │ • Lỗi: Log SLF4J, rethrow gốc     │
        └───────────────────────────────┘ └───────────────────────────────────┘
```

### Nhóm 1: Thay đổi nghiệp vụ thành công (Business Success)
- **Ý nghĩa:** Các hành vi tác động thay đổi trạng thái dữ liệu thành công trong hệ thống (tạo, cập nhật, hủy, đổi quyền, đổi cấu hình).
- **Cơ chế Transaction:** `Propagation.REQUIRED`. Audit log được ghi trong **cùng transaction** với thao tác thay đổi nghiệp vụ đó.
- **Tính toàn vẹn:** Thao tác nghiệp vụ và bản ghi audit log cùng commit hoặc cùng rollback.
  - Nếu nghiệp vụ rollback, log tương ứng không tồn tại (tránh việc log nhận vơ thay đổi chưa từng xảy ra).
  - Nếu ghi log thất bại (ví dụ lỗi DB, constraint), **không được nuốt lỗi**; exception được đẩy ra ngoài để toàn bộ nghiệp vụ cha rollback theo, đảm bảo không có thay đổi nào xảy ra mà thiếu dấu vết.

### Nhóm 2: Sự kiện thất bại cần điều tra (Investigative Failure)
- **Ý nghĩa:** Các hành vi vi phạm an ninh hoặc lỗi bảo mật cần lưu vết để điều tra (đăng nhập thất bại do sai mật khẩu, tài khoản bị vô hiệu hóa cố đăng nhập, truy cập bị từ chối quyền - 403 Forbidden, các hành vi nghi vấn).
- **Cơ chế Transaction:** `Propagation.REQUIRES_NEW`. Audit log được ghi trong **transaction riêng biệt độc lập**.
- **Tính toàn vẹn:** Phải được gọi và commit độc lập **trước khi ném exception** ra ngoài tầng Web. Do đó, bản ghi audit log vẫn tồn tại vĩnh viễn trong CSDL kể cả khi nghiệp vụ cha rollback.
- **Quy tắc xử lý lỗi:** Trong trường hợp hiếm gặp khi CSDL audit log bị lỗi, hệ thống bắt ngoại lệ và ghi log cảnh báo ra hệ thống (`logger.error(...)`), tuyệt đối không làm gián đoạn hoặc nuốt mất exception lỗi bảo mật ban đầu ném về phía client.

---

## 3. Ma trận Phân loại Logic và Danh mục Audit Action

Toàn bộ các hành động kiểm toán được định danh tập trung bằng enum `AuditAction`, không sử dụng chuỗi tự do (string literal):

| Nhóm | `AuditAction` Enum | Bounded Context / Logic kích hoạt | Entity Type | Entity ID | Dữ liệu Metadata |
|---|---|---|---|---|---|
| **Nhóm 1 (REQUIRED)** | `CREATE_CONCERT` | Admin/Organizer tạo mới concert | `CONCERT` | ID concert | Thông tin cơ bản concert, organizer_id |
| **Nhóm 1 (REQUIRED)** | `UPDATE_CONCERT` | Cập nhật cấu hình, mô tả concert | `CONCERT` | ID concert | Snapshot `before` và `after` (các trường thay đổi) |
| **Nhóm 1 (REQUIRED)** | `PUBLISH_CONCERT`| Chuyển trạng thái concert sang PUBLISHED | `CONCERT` | ID concert | Trạng thái trước và sau |
| **Nhóm 1 (REQUIRED)** | `CANCEL_CONCERT` | Hủy concert | `CONCERT` | ID concert | Lý do hủy (`reason`), trạng thái trước/sau |
| **Nhóm 1 (REQUIRED)** | `CREATE_SEAT_ZONE` | Tạo khu vực chỗ ngồi cho concert | `SEAT_ZONE` | ID zone | Code, name, capacity, concert_id |
| **Nhóm 1 (REQUIRED)** | `UPDATE_SEAT_ZONE` | Sửa khu vực chỗ ngồi | `SEAT_ZONE` | ID zone | Snapshot `before` và `after` |
| **Nhóm 1 (REQUIRED)** | `CREATE_TICKET_TYPE` | Tạo loại vé bán | `TICKET_TYPE` | ID ticket type | Name, price, total_quantity, zone_id |
| **Nhóm 1 (REQUIRED)** | `UPDATE_TICKET_TYPE` | Sửa cấu hình loại vé, giá, quota | `TICKET_TYPE` | ID ticket type | Snapshot `before` và `after` |
| **Nhóm 1 (REQUIRED)** | `UPDATE_USER_STATUS` | Admin khóa/mở khóa/kích hoạt user | `USER` | ID user | `old_status`, `new_status` |
| **Nhóm 1 (REQUIRED)** | `UPDATE_USER_ROLE` | Admin phân bổ role (ADMIN, ORGANIZER, ...) | `USER` | ID user | `old_role`, `new_role` |
| **Nhóm 1 (REQUIRED)** | `CREATE_USER` | Admin tạo tài khoản nội bộ | `USER` | ID user | Email (che), role, status ban đầu |
| **Nhóm 1 (REQUIRED)** | `CREATE_ARTIST_BIO_JOB` | Upload và kích hoạt AI trích xuất bio | `ARTIST_BIO_JOB` | ID job | `concert_id`, filename đã upload |
| **Nhóm 1 (REQUIRED)** | `READ_AUDIT_LOGS` | Admin truy vấn hoặc xem chi tiết audit trail | `AUDIT_LOG` | ID log / "LIST" | Filter parameters, pagination info |
| **Nhóm 1 (REQUIRED)** | `HOLD_ORDER` | Khán giả giữ vé thành công, tạo order `HELD` | `ORDER` | ID order | Items (`ticket_type_id`, `quantity`), tổng tiền, `hold_expires_at` |
| **Nhóm 1 (REQUIRED)** | `RELEASE_ORDER` | `OrderExpirationScheduler` chuyển order `HELD → EXPIRED`, hoàn kho và quota (nguồn nhả kho duy nhất) | `ORDER` | ID order | Items hoàn kho, `hold_expires_at` |
| **Nhóm 1 (REQUIRED)** | `CREATE_PAYMENT` | Tạo lượt thanh toán (`INITIATED` / `PENDING`) | `PAYMENT` | ID payment | `order_id`, `provider`, `merchant_txn_ref`, `amount` |
| **Nhóm 1 (REQUIRED)** | `COMPLETE_PAYMENT` | Payment `SUCCESS` + order `CONFIRMED` + settle kho + phát vé | `PAYMENT` | ID payment | `order_id`, `provider_txn_id`, `source` (IPN / QUERY_API), `payment_event_id` |
| **Nhóm 1 (REQUIRED)** | `FAIL_PAYMENT` | Payment chuyển `FAILED` / `CANCELLED` (cổng từ chối, khách hủy, query báo không tồn tại) | `PAYMENT` | ID payment | `new_status`, `failure_code`, `source` |
| **Nhóm 1 (REQUIRED)** | `EXPIRE_PAYMENT` | Worker đối soát chuyển payment `EXPIRED` (không đụng order/kho) | `PAYMENT` | ID payment | `order_id`, kết quả query |
| **Nhóm 1 (REQUIRED)** | `RECORD_LATE_PAYMENT` | Tiền về sau khi hold hết hạn: payment `SUCCESS`, không phát vé | `PAYMENT` | ID payment | `order_id`, `hold_expires_at`, `paid_at`, `payment_event_id` |
| **Nhóm 1 (REQUIRED)** | `REQUEST_REFUND` | Tạo lệnh hoàn tiền `REQUESTED` | `REFUND` | ID refund | `payment_id`, `amount`, `reason`, `idempotency_key` |
| **Nhóm 1 (REQUIRED)** | `COMPLETE_REFUND` | Refund `SUCCESS` (payment giữ nguyên `SUCCESS`) | `REFUND` | ID refund | `payment_id`, `provider_refund_id`, `amount` |
| **Nhóm 1 (REQUIRED)** | `FAIL_REFUND` | Refund `FAILED`, cần đối soát thủ công | `REFUND` | ID refund | `payment_id`, `failure_code` |
| **Nhóm 2 (REQUIRES_NEW)** | `LOGIN_FAILED` | Đăng nhập thất bại (sai pass, tài khoản lock/pending) | `USER` | Email (masked) | Lý do (`BAD_CREDENTIALS`, `LOCKED`, `UNVERIFIED`) |
| **Nhóm 2 (REQUIRES_NEW)** | `ACCESS_DENIED` | Truy cập bị từ chối do không đủ quyền (403) | `SECURITY` | Request URI | Role của caller, HTTP method, required roles |
| **Nhóm 2 (REQUIRES_NEW)** | `SUSPICIOUS_REQUEST` | Phát hiện token giả mạo, bất thường bảo mật | `SECURITY` | IP Address | Mô tả hành vi nghi vấn |
| **Nhóm 2 (REQUIRES_NEW)** | `PAYMENT_WEBHOOK_REJECTED` | IPN/Query bị từ chối: sai chữ ký, lệch số tiền, không tìm thấy payment | `PAYMENT` | ID payment hoặc `merchant_txn_ref` nhận được | `reason` (`INVALID_SIGNATURE`, `AMOUNT_MISMATCH`, `PAYMENT_NOT_FOUND`), `provider`, `payment_event_id` |
| **Nhóm 2 (REQUIRES_NEW)** | `PAYMENT_RESULT_CONFLICT` | Kết quả cổng mâu thuẫn với kết quả đã ghi (không ghi đè) | `PAYMENT` | ID payment | `stored_status`, `incoming_result`, `payment_event_id` |

> [!NOTE]
> **Phân vai với `payment_events`:** `payment_events` lưu nguyên văn "cổng đã báo gì" (raw payload, chống trùng). `audit_logs` chỉ ghi "TicketBox đã thay đổi gì / ai làm" và cảnh báo bảo mật; metadata chỉ tham chiếu `payment_event_id`, **không** sao chép raw payload hay chữ ký.
> **Không audit:** hold thất bại (hết vé, vượt `max_per_user`), IPN/query trùng, bước trung gian `INITIATED → PENDING` và refund `REQUESTED → PROCESSING`, URL đến muộn bị từ chối, ReturnUrl.

---

## 4. Ranh giới Thiết kế của AuditLogService

`AuditLogService` (hay `AppLogService`) đóng vai trò là **cổng duy nhất (Single Point of Entry)** để ghi audit log trong toàn bộ ứng dụng:

```java
public interface AuditLogService {

    // === NHÓM 1: THAY ĐỔI NGHIỆP VỤ THÀNH CÔNG (REQUIRED) ===
    void logBusinessSuccess(AuditAction action, String entityType, String entityId, Map<String, Object> metadata);

    // === NHÓM 2: SỰ KIỆN THẤT BẠI CẦN ĐIỀU TRA (REQUIRES_NEW) ===
    void logInvestigativeFailure(AuditAction action, String entityType, String entityId, Map<String, Object> metadata);
}
```

### Ranh giới trách nhiệm:
1. **Phân tách nhóm phương thức:** Tách bạch rõ 2 phương thức đại diện cho 2 cơ chế transaction để lập trình viên các module không phải tự quản lý hay tự cấu hình `Propagation`.
2. **Không quyết định nghiệp vụ:** Service chỉ tiếp nhận dữ liệu và thực hiện lưu trữ, tuyệt đối không can thiệp hay ra quyết định về logic nghiệp vụ của caller.
3. **Tự động trích xuất ngữ cảnh (Context Resolution):**
   - **Actor (`User`):** Tự động lấy từ `SecurityContextHolder` (`CustomUserDetails`). Nếu là request ẩn danh (chưa đăng nhập) hoặc chạy từ background worker, `actor` là `null`.
   - **Client IP & User-Agent:** Tự động lấy từ `RequestContextHolder` (`HttpServletRequest`), ưu tiên đọc header `X-Forwarded-For` (lấy IP đầu tiên), fallback sang `request.getRemoteAddr()`. Caller không cần và không được phép truyền thủ công các thông số này.
4. **Sanitization & Masking:** Tự động kiểm tra và che giấu các trường nhạy cảm trong `metadata` (email, card, phone, token) trước khi chuyển thành JSONB.

---

## 5. Quy tắc Quản lý Dữ liệu và Bảo mật

1. **Chuẩn hóa thời gian:** Cột `created_at` lưu trữ theo kiểu `TIMESTAMPTZ` (UTC). Khi trả về qua API, định dạng ISO 8601 kèm múi giờ chuẩn (ví dụ: `2026-10-14T10:15:30Z`).
2. **Che giấu dữ liệu (Data Masking):**
   - Email: `john.doe@example.com` → `j***e@example.com`
   - Số điện thoại: `0901234567` → `090****567`
   - Tuyệt đối cấm lưu: mật khẩu, secret key, access/refresh token, payload JWT.
3. **Quyền truy cập & Bất biến:**
   - Ứng dụng chỉ có quyền `INSERT` và `SELECT` trên bảng `audit_logs`.
   - Không cung cấp API `PUT`, `PATCH`, `DELETE` cho tài nguyên `audit-logs`.
   - Chỉ người dùng có vai trò `ADMIN` mới được phép gọi API tra cứu audit log.
   - Mỗi lần Admin truy vấn log (`GET /admin/audit-logs`), hệ thống tự động ghi lại một sự kiện audit `READ_AUDIT_LOGS` để giám sát chính người giám sát (Auditing the Auditors).

---

## 6. Kịch bản Xử lý Ngoại lệ

- **Lỗi Database khi ghi Nhóm 1 (REQUIRED):**
  - DB ném `DataAccessException`. Ngoại lệ không bị bắt tại `AuditLogService` mà lan truyền ra ngoài, làm transaction hiện tại rollback toàn bộ các thay đổi nghiệp vụ.
- **Lỗi Database khi ghi Nhóm 2 (REQUIRES_NEW):**
  - Khối `try-catch` trong `logInvestigativeFailure` bắt `Exception`, ghi log `logger.error("Failed to record investigative audit log: {}", e.getMessage(), e)` ra log hệ thống và nuốt lỗi nội bộ này để tiếp tục cho phép ném exception bảo mật ban đầu (như `InvalidCredentialsException`, `AccessDeniedException`) về phía client.
- **Không tìm thấy Actor hoặc Request Context (Background job / Anonymous):**
  - Ghi `actor_user_id = null`, `ip_address = null` (hoặc `"SYSTEM"`), `user_agent = null`.

---

## 7. Tiêu chí Chấp nhận (Acceptance Criteria)

1. [x] Đầy đủ 2 nhóm phương thức với transaction propagation chính xác (`REQUIRED` vs `REQUIRES_NEW`).
2. [x] Không nuốt lỗi ở Nhóm 1; có nuốt lỗi an toàn ở Nhóm 2 kèm log hệ thống.
3. [x] Toàn bộ action dùng centralized enum `AuditAction`.
4. [x] Actor, IP và User-Agent được lấy tự động từ ngữ cảnh Security và Request.
5. [x] Dữ liệu nhạy cảm được che giấu đúng chuẩn.
6. [x] API tra cứu audit log chỉ mở cho ADMIN và có cơ chế ghi log khi đọc.
7. [x] Bảng `audit_logs` bất biến, không hỗ trợ sửa/xóa qua bất kỳ API nào.