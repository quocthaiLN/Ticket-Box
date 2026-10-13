# TicketBox — Audit Log API Design

Tài liệu này thiết kế API tra cứu và điều tra nhật ký kiểm toán (Audit Trail) dành riêng cho Quản trị viên (Admin).
Quy ước chung tuân thủ theo `blueprint/api-design/details/base-api.md`.

Nguồn nghiệp vụ chính:
- `blueprint/specs/details/16-audit-logging.md`
- `blueprint/database-design/details/audit_logs.md`

---

## 1. Mục tiêu

- Cung cấp giao diện REST API an toàn, có kiểm soát để Admin tra cứu, lọc và điều tra các sự kiện hệ thống.
- Đảm bảo tính bất biến (Immutability): API chỉ hỗ trợ thao tác đọc (`GET`), tuyệt đối không cung cấp endpoint tạo, sửa hay xóa.
- Áp dụng kiểm soát truy cập nghiêm ngặt: Chỉ người dùng có vai trò `ADMIN` mới có quyền truy cập.
- Cơ chế "Auditing the Auditors": Mỗi thao tác đọc hoặc truy vấn audit trail đều được ghi nhận lại trong audit log với hành động `READ_AUDIT_LOGS`.

---

## 2. Resource và Ánh Xạ Cơ Sở Dữ Liệu

| Resource | Bảng CSDL | Vai trò | Quyền hạn |
|---|---|---|---|
| `audit_log` | `audit_logs` | Lưu trữ nhật ký kiểm toán bất biến | `ADMIN` (chỉ đọc) |

Bảng `audit_logs` không cho phép cập nhật hoặc xóa thông qua API:
- `INSERT`: Chỉ thực hiện nội bộ qua `AuditLogService`.
- `SELECT`: Thông qua các endpoint bên dưới.
- `UPDATE` / `DELETE`: Bị cấm hoàn toàn.

---

## 3. Tổng Hợp Endpoint

| Method | Endpoint | Quyền (Auth) | Mục đích |
|---|---|---|---|
| `GET` | `/admin/audit-logs` | `ADMIN` | Tra cứu danh sách audit log có lọc, phân trang và sắp xếp. |
| `GET` | `/admin/audit-logs/{id}` | `ADMIN` | Xem thông tin chi tiết một bản ghi audit log kèm metadata. |

---

## 4. API Chi Tiết

### 4.1. `GET /admin/audit-logs`

Tra cứu danh sách audit logs theo nhiều tiêu chí lọc kết hợp phân trang.

#### Query Parameters:

| Tham số | Kiểu dữ liệu | Mặc định | Mô tả |
|---|---|---|---|
| `page` | `Integer` | `0` | Số thứ tự trang (0-indexed). |
| `size` | `Integer` | `20` | Kích thước trang (tối đa 100). |
| `action` | `String (Enum)` | `null` | Lọc theo hành động kiểm toán (ví dụ: `UPDATE_CONCERT`, `LOGIN_FAILED`). |
| `actor_user_id`| `UUID` | `null` | Lọc theo ID người thực hiện. |
| `entity_type` | `String` | `null` | Lọc theo loại đối tượng (ví dụ: `CONCERT`, `USER`, `SEAT_ZONE`). |
| `entity_id` | `String` | `null` | Lọc theo ID đối tượng bị tác động. |
| `from` | `DateTime` (ISO-8601)| `null` | Lọc thời điểm bắt đầu (ví dụ: `2026-10-01T00:00:00Z`). |
| `to` | `DateTime` (ISO-8601)| `null` | Lọc thời điểm kết thúc (ví dụ: `2026-10-14T23:59:59Z`). |

#### Response `200 OK`:

```json
{
  "data": [
    {
      "id": "7b8d4e9b-325f-4a61-9c6a-4933a8848f32",
      "actor": {
        "id": "11111111-1111-4111-8111-111111111111",
        "email": "admin@ticketbox.vn",
        "full_name": "System Administrator"
      },
      "action": "UPDATE_CONCERT",
      "entity_type": "CONCERT",
      "entity_id": "22222222-2222-4222-8222-222222222222",
      "ip_address": "192.168.1.100",
      "user_agent": "Mozilla/5.0 (X11; Linux x86_64)",
      "created_at": "2026-10-14T00:30:00Z"
    },
    {
      "id": "8c9e5f0c-436a-4b72-ad7b-5044b9959e43",
      "actor": null,
      "action": "LOGIN_FAILED",
      "entity_type": "USER",
      "entity_id": "u***@example.com",
      "ip_address": "203.0.113.42",
      "user_agent": "PostmanRuntime/7.39.0",
      "created_at": "2026-10-14T00:28:15Z"
    }
  ],
  "pagination": {
    "page": 0,
    "page_size": 20,
    "total_items": 42,
    "total_pages": 3,
    "has_more": true
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

#### Side Effects:
- Ghi nhận sự kiện audit: `action = READ_AUDIT_LOGS`, `entity_type = AUDIT_LOG`, `entity_id = "LIST"`.

---

### 4.2. `GET /admin/audit-logs/{id}`

Xem toàn bộ nội dung chi tiết của một bản ghi audit log, bao gồm cả dữ liệu metadata dạng JSONB.

#### Path Parameters:
- `id` (`UUID`, bắt buộc): Mã định danh duy nhất của bản ghi audit log.

#### Response `200 OK`:

```json
{
  "data": {
    "id": "7b8d4e9b-325f-4a61-9c6a-4933a8848f32",
    "actor": {
      "id": "11111111-1111-4111-8111-111111111111",
      "email": "admin@ticketbox.vn",
      "full_name": "System Administrator"
    },
    "action": "UPDATE_CONCERT",
    "entity_type": "CONCERT",
    "entity_id": "22222222-2222-4222-8222-222222222222",
    "metadata": {
      "before": {
        "title": "Anh Trai Say Hi Live Concert 2026",
        "status": "DRAFT"
      },
      "after": {
        "title": "Anh Trai Say Hi Live Concert 2026 (Chính Thức)",
        "status": "PUBLISHED"
      }
    },
    "ip_address": "192.168.1.100",
    "user_agent": "Mozilla/5.0 (X11; Linux x86_64)",
    "created_at": "2026-10-14T00:30:00Z"
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

#### Response `404 Not Found`:
Khi không tìm thấy ID bản ghi tương ứng (`application/problem+json`):
```json
{
  "type": "https://api.ticketbox.vn/errors/audit-log-not-found",
  "title": "AUDIT_LOG_NOT_FOUND",
  "status": 404,
  "detail": "Audit log record not found with id: 7b8d4e9b-325f-4a61-9c6a-4933a8848f32",
  "instance": "/admin/audit-logs/7b8d4e9b-325f-4a61-9c6a-4933a8848f32",
  "code": "AUDIT_LOG_NOT_FOUND",
  "details": {
    "id": "7b8d4e9b-325f-4a61-9c6a-4933a8848f32"
  },
  "traceId": "req_01JX9Q6N4E",
  "request_id": "req_01JX9Q6N4E"
}
```

#### Side Effects:
- Ghi nhận sự kiện audit: `action = READ_AUDIT_LOGS`, `entity_type = AUDIT_LOG`, `entity_id = {id}`.

---

## 5. Danh Mục Mã Lỗi (Error Catalog)

| HTTP Status | Mã lỗi (`code`) | Tình huống xảy ra |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Định dạng UUID không hợp lệ hoặc tham số ngày tháng sai format RFC 3339 / ISO 8601. |
| `401` | `UNAUTHORIZED` | Không có JWT hoặc token đã hết hạn / bị thu hồi. |
| `403` | `FORBIDDEN` | Người dùng đã đăng nhập nhưng không có vai trò `ADMIN` (ví dụ: `AUDIENCE`, `ORGANIZER`, `CHECKER`). |
| `404` | `AUDIT_LOG_NOT_FOUND` | Không tồn tại bản ghi audit log với ID được yêu cầu. |

---

## 6. Tiêu Chí Chấp Nhận & Kiểm Thử

- [x] Chỉ người dùng mang `ROLE_ADMIN` mới có thể gọi thành công các endpoint này.
- [x] Người dùng `AUDIENCE`, `ORGANIZER`, `CHECKER` khi gọi đến phải nhận phản hồi `403 Forbidden` (và phát sinh sự kiện audit `ACCESS_DENIED`).
- [x] Phân trang hoạt động chính xác với thông số `page` và `size`, sắp xếp mặc định theo `created_at DESC`.
- [x] Lọc chính xác theo `action`, `actor_user_id`, `entity_type`, `entity_id`, khoảng thời gian `from` và `to`.
- [x] Toàn bộ dữ liệu nhạy cảm được che (masked) trong trường metadata và entity_id nếu có.
- [x] Bản thân hành vi truy vấn log phát sinh bản ghi `READ_AUDIT_LOGS`.
