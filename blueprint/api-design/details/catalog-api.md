# TicketBox — Catalog API Design

Tài liệu này thiết kế API cho **Catalog Module**. Module này phục vụ hai nhóm nhu cầu:

- Public read cực cao cho trang danh sách concert, trang chi tiết, metadata, seat map và số vé còn lại.
- Admin/Organizer quản lý concert (venue là chuỗi), seat zone, ticket type và trạng thái xuất bản.

**Phạm vi:** phần Admin mô tả 9 endpoint tại mục 5.2 theo schema `backend/src/main/resources/db/migration/V1__init_schema.sql`, đối chiếu entity/DTO/service. Mục 5.1 và toàn bộ mục 6 (Public API) giữ contract hiện có; các ví dụ venue object/ID có prefix tại đó không áp dụng cho Admin.

Các tài liệu tham khảo (nếu khác schema, phần Admin ưu tiên schema):

- `blueprint/specs/09-concert-catalog.md`
- `blueprint/specs/14-caching.md`
- `blueprint/specs/15-rate-limiting-anti-bot.md`
- `blueprint/database-design.md`
- quy ước chung trong `blueprint/api-design/base-api.md`

---

## 1. Mục tiêu

Catalog là module chịu tải đọc lớn nhất của TicketBox, đặc biệt trong các phút đầu mở bán khi nhiều người dùng cùng vào trang chủ hoặc trang chi tiết concert.

Mục tiêu kỹ thuật:

- Trang danh sách và chi tiết concert không truy vấn trực tiếp PostgreSQL ở mọi request.
- Metadata tĩnh được cache dài qua Redis/CDN.
- Seat map/SVG và ảnh fallback được phục vụ qua CDN/Object Storage.
- Số vé còn lại được đọc từ Redis inventory read model với TTL ngắn.
- UI chấp nhận eventual consistency cho số vé hiển thị; quyết định bán vé cuối cùng vẫn thuộc Ticketing Module bằng transaction PostgreSQL.
- Khi origin/Redis lỗi, hệ thống graceful degradation thay vì làm trắng trang.

---

## 2. Base URL và chuẩn response

| Môi trường | Base URL | Ghi chú |
| --- | --- | --- |
| API Gateway | `https://api.ticketbox.vn/v1` | Public dynamic API và admin API. |
| CDN Metadata | `https://cdn.ticketbox.vn/api/v1` | Metadata tĩnh, seat map metadata, asset URLs. |
| Local | `http://localhost:8080/v1` | Chạy backend Spring Boot local. |

Tất cả response JSON dùng envelope:

```json
{
  "data": {},
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

Ví dụ pagination public cũ (Admin dùng page/size tại mục 7.1):

```json
{
  "data": [],
  "pagination": {
    "next_cursor": "eyJzdGFydHNfYXQiOiIyMDI2LTA4LTEwIn0",
    "has_more": true,
    "limit": 20
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

Lỗi dùng RFC 7807:

```json
{
  "type": "https://api.ticketbox.vn/errors/concert-not-found",
  "title": "Concert không tồn tại",
  "status": 404,
  "code": "CONCERT_NOT_FOUND",
  "detail": "Concert không tồn tại hoặc chưa được công bố.",
  "instance": "/v1/concerts/crt_01JX9Q2M5P7KZ3R4N8Y6",
  "request_id": "req_01JX9Q6N4E"
}
```

---

## 3. Domain model và mapping database

Mapping dưới đây là nguồn dữ liệu cho contract Admin; không thay đổi contract public tại mục 6.

| Resource | Bảng/cột | Ràng buộc chính |
| --- | --- | --- |
| Concert | `concerts` | UUID; `organizer_id` tham chiếu `users.id`; `slug` unique toàn hệ thống; `ends_at > starts_at`. |
| Venue | `concerts.venue` | Chuỗi bắt buộc, tối đa 255 ký tự. Không có bảng `venues`, `venue_id` hoặc capacity venue. |
| Seat zone | `seat_zones` | UUID; unique `(concert_id, code)`; `capacity > 0`; `sort_order` mặc định 0. |
| Ticket type | `ticket_types` | UUID; unique `(concert_id, name)`; FK composite `(seat_zone_id, concert_id)` đảm bảo zone cùng concert. |
| Artist bio | `concerts.artist_bio` | TEXT nullable. |
| Inventory | `ticket_types` | `total_quantity`, `held_quantity`, `sold_quantity` không âm; total >= held + sold. `available_quantity = total_quantity - held_quantity - sold_quantity` là computed field. |

Cả ba bảng catalog có `created_at`, `updated_at` kiểu TIMESTAMPTZ. Schema không có `published_at`, `cancelled_at`, hoặc lý do hủy trên concert. Không suy diễn cột lưu trữ từ response public cũ.

- Concert status lưu trong DB: `DRAFT`, `PUBLISHED`, `CANCELED`, `CANCELLED`, `COMPLETED`. Entity dùng `CANCELED`; response Admin chuẩn hóa cả hai cách viết trạng thái hủy thành `CANCELED`. Filter `CANCELED` hoặc alias `CANCELLED` đều tìm cả hai giá trị lưu trữ.
- Ticket type status: `DRAFT`, `ACTIVE`, `ON_SALE`, `SUSPENDED`, `CLOSED`, `SOLD_OUT`.
- Giá là `NUMERIC(12,2)` không âm; currency VARCHAR(3), mặc định `VND`; `max_per_user > 0`; `sale_end_at > sale_start_at`.
- Các giới hạn tổng vé theo capacity zone và lifecycle ở mục 7 là business rules bổ sung, không phải CHECK constraint sẵn có trong DB.

---

## 4. RBAC

| Nhóm endpoint | Auth | Quyền |
| --- | --- | --- |
| Public catalog read | Không bắt buộc JWT | Guest và AUDIENCE đều xem được concert `PUBLISHED`. |
| Public inventory | Không bắt buộc JWT | Bị rate limit theo IP + concert. |
| Admin catalog read/write | `ORGANIZER` | Tạo concert; chỉ liệt kê và thao tác concert có `organizer_id = current_user.id`, gồm cả zone/ticket type thuộc concert đó. |
| Admin catalog read/write | `ADMIN` | Mọi quyền catalog của ORGANIZER trên mọi concert, vẫn tuân thủ validation và lifecycle. |

Backend kiểm tra role và ownership, không chỉ dựa vào API Gateway. Với PATCH zone/ticket type, lấy concert cha từ DB để kiểm tra quyền; không tin concert ID do client cung cấp. Resource tồn tại nhưng không thuộc ORGANIZER trả `403 FORBIDDEN`; không tồn tại trả `404` tương ứng. List lọc ownership trước khi phân trang và tính tổng.

ADMIN còn có quyền nâng AUDIENCE thành ORGANIZER. Quyền này thuộc Auth/User, ngoài 9 endpoint catalog. `AdminUserController` hiện có API đổi status user, chưa có API nâng role; tài liệu này không thêm hoặc giả định endpoint đó đã tồn tại.

---

## 5. Endpoint tổng hợp

### 5.1. Public endpoints

| Method | Endpoint | Auth | Mục đích |
| --- | --- | --- | --- |
| `GET` | `/concerts` | Public | Danh sách concert `PUBLISHED`. |
| `GET` | `/concerts/{concert_id}` | Public | Chi tiết concert cho trang detail. |
| `GET` | `/concerts/{concert_id}/metadata` | Public | Metadata tĩnh, venue, zones, bio, asset URLs. |
| `GET` | `/concerts/{concert_id}/seat-map` | Public| Sơ đồ SVG hoặc metadata seat map. |
| `GET` | `/concerts/{concert_id}/ticket-types` | Public | Danh sách loại vé được hiển thị. |
| `GET` | `/concerts/{concert_id}/inventory` | Public | Tồn kho gần thời gian thực từ Redis. |

### 5.2. Admin/Organizer endpoints

| Method | Endpoint | Auth | Mục đích |
| --- | --- | --- | --- |
| `GET` | `/admin/concerts` | `ORGANIZER`, `ADMIN` | Danh sách concert quản trị, có cả draft/cancelled. |
| `POST` | `/admin/concerts` | `ORGANIZER`, `ADMIN` | Tạo concert ở `DRAFT`. |
| `PATCH` | `/admin/concerts/{concert_id}` | `ORGANIZER`, `ADMIN` | Cập nhật concert. |
| `POST` | `/admin/concerts/{concert_id}/publish` | `ORGANIZER`, `ADMIN` | Publish concert. |
| `POST` | `/admin/concerts/{concert_id}/cancel` | `ORGANIZER`, `ADMIN` | Hủy concert. |
| `POST` | `/admin/concerts/{concert_id}/seat-zones` | `ORGANIZER`, `ADMIN` | Tạo khu vực. |
| `PATCH` | `/admin/seat-zones/{seat_zone_id}` | `ORGANIZER`, `ADMIN` | Cập nhật khu vực. |
| `POST` | `/admin/concerts/{concert_id}/ticket-types` | `ORGANIZER`, `ADMIN` | Tạo loại vé. |
| `PATCH` | `/admin/ticket-types/{ticket_type_id}` | `ORGANIZER`, `ADMIN` | Cập nhật loại vé. |

---

## 6. Public API chi tiết

### 6.1. `GET /concerts`

Trả danh sách concert công khai cho trang chủ/search.

**Query parameters**

| Tên | Kiểu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `q` | string | Không | Tìm theo `title` hoặc `artist_name`. |
| `city` | string | Không | Lọc theo `venues.city`. |
| `from` | datetime | Không | Chỉ lấy concert bắt đầu từ thời điểm này. |
| `to` | datetime | Không | Chỉ lấy concert bắt đầu trước thời điểm này. |
| `limit` | number | Không | Mặc định `20`, tối đa `100`. |
| `cursor` | string | Không | Cursor trang tiếp theo. |
| `sort` | string | Không | `starts_at`, `-starts_at`, `title`. |

Public endpoint luôn ép `concerts.status = PUBLISHED`. Nếu client gửi `status`, server bỏ qua hoặc trả `400` tùy policy triển khai.

**Response `200`**

```json
{
  "data": [
    {
      "id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
      "title": "Anh Trai Say Hi",
      "slug": "anh-trai-say-hi",
      "artist_name": "Various Artists",
      "starts_at": "2026-08-10T12:00:00Z",
      "ends_at": "2026-08-10T16:00:00Z",
      "status": "PUBLISHED",
      "cover_image_url": "https://cdn.ticketbox.vn/concerts/anh-trai-say-hi.webp",
      "venue": {
        "id": "ven_01JX9Q2N",
        "name": "Sân vận động Mỹ Đình",
        "city": "Hà Nội"
      },
      "ticket_price_range": {
        "min_amount": 500000,
        "max_amount": 4500000,
        "currency": "VND"
      }
    }
  ],
  "pagination": {
    "next_cursor": "eyJzdGFydHNfYXQiOiIyMDI2LTA4LTEwIn0",
    "has_more": true,
    "limit": 20
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

**Cache**

- Redis key: `catalog:list:{hash(query)}`
- TTL: `5-30 phút`
- Header gợi ý: `Cache-Control: public, s-maxage=300, stale-while-revalidate=600`
- Với query phổ biến, CDN có thể cache.

---

### 6.2. `GET /concerts/{concert_id}`

Trả chi tiết concert cho web/mobile. Endpoint này thuận tiện cho client app; page render nặng nên ưu tiên gọi thêm `/metadata` và `/inventory` song song.

**Response `200`**

```json
{
  "data": {
    "id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
    "title": "Anh Trai Say Hi",
    "slug": "anh-trai-say-hi",
    "description": "Concert âm nhạc quy mô lớn.",
    "artist_name": "Various Artists",
    "starts_at": "2026-08-10T12:00:00Z",
    "ends_at": "2026-08-10T16:00:00Z",
    "status": "PUBLISHED",
    "cover_image_url": "https://cdn.ticketbox.vn/concerts/anh-trai-say-hi.webp",
    "venue": {
      "id": "ven_01JX9Q2N",
      "name": "Sân vận động Mỹ Đình",
      "address": "Lê Đức Thọ, Nam Từ Liêm",
      "city": "Hà Nội",
      "map_url": "https://maps.example/my-dinh"
    },
    "artist_bio": "Bản giới thiệu ngắn gọn do AI hỗ trợ biên tập."
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

**Ràng buộc**

- Guest chỉ xem được `PUBLISHED`.
- Nếu concert là `DRAFT` hoặc không thuộc quyền của caller, trả `404 CONCERT_NOT_FOUND` thay vì lộ dữ liệu.
- Admin cần xem draft dùng `/admin/concerts/{concert_id}` nếu endpoint đó được bổ sung ở phase sau.

---

### 6.3. `GET /concerts/{concert_id}/metadata`

Endpoint tối ưu cho CDN. Trả dữ liệu tĩnh để render trang chi tiết, seat zones và artist bio.

**Response `200`**

```json
{
  "data": {
    "concert": {
      "id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
      "title": "Anh Trai Say Hi",
      "slug": "anh-trai-say-hi",
      "description": "Concert âm nhạc quy mô lớn.",
      "artist_name": "Various Artists",
      "starts_at": "2026-08-10T12:00:00Z",
      "ends_at": "2026-08-10T16:00:00Z",
      "status": "PUBLISHED",
      "cover_image_url": "https://cdn.ticketbox.vn/concerts/anh-trai-say-hi.webp"
    },
    "venue": {
      "id": "ven_01JX9Q2N",
      "name": "Sân vận động Mỹ Đình",
      "address": "Lê Đức Thọ, Nam Từ Liêm",
      "city": "Hà Nội",
      "map_url": "https://maps.example/my-dinh"
    },
    "seat_zones": [
      {
        "id": "zon_01JX9Q4A",
        "code": "SVIP",
        "name": "SVIP",
        "description": "Khu vực gần sân khấu.",
        "capacity": 200,
        "svg_path": "M10 10 H120 V80 H10 Z",
        "sort_order": 1
      }
    ],
    "ticket_types": [
      {
        "id": "tkt_01JX9Q5A",
        "seat_zone_id": "zon_01JX9Q4A",
        "name": "SVIP",
        "description": "Khu vực gần sân khấu.",
        "price": {
          "amount": 4500000,
          "currency": "VND"
        },
        "max_per_user": 2,
        "sale_start_at": "2026-07-01T03:00:00Z",
        "sale_end_at": "2026-08-09T17:00:00Z",
        "status": "ON_SALE"
      }
    ],
    "seat_map": {
      "svg_url": "https://cdn.ticketbox.vn/seat-maps/crt_01JX9Q2M.svg",
      "fallback_image_url": "https://cdn.ticketbox.vn/seat-maps/crt_01JX9Q2M.webp"
    },
    "artist_bio": "Bản giới thiệu ngắn gọn."
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E",
    "cache": {
      "source": "redis",
      "ttl_seconds": 86400
    }
  }
}
```

**Headers**

```http
Cache-Control: public, max-age=3600, s-maxage=86400, stale-while-revalidate=3600, stale-if-error=86400
ETag: "catalog-metadata-crt_01JX9Q2M-v7"
```

**Cache behavior**

- CDN cache hit phải trả trực tiếp từ edge.
- Cache miss về origin phải dùng SingleFlight để tránh nhiều request cùng đập vào DB.
- Khi admin update concert, venue, zone, ticket type hoặc active bio, backend phải purge/invalidate:
  - `catalog:concert:{concert_id}`
  - `catalog:metadata:{concert_id}`
  - `catalog:list:*`
  - CDN URL `/api/v1/concerts/{concert_id}/metadata`

---

### 6.4. `GET /concerts/{concert_id}/seat-map`

Trả metadata sơ đồ hoặc redirect tới asset CDN.

**Query parameters**

| Tên | Kiểu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `format` | `json`, `svg`, `image` | Không | Mặc định `json`. |

**Response `200` với `format=json`**

```json
{
  "data": {
    "concert_id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
    "svg_url": "https://cdn.ticketbox.vn/seat-maps/crt_01JX9Q2M.svg",
    "fallback_image_url": "https://cdn.ticketbox.vn/seat-maps/crt_01JX9Q2M.webp",
    "zones": [
      {
        "seat_zone_id": "zon_01JX9Q4A",
        "code": "SVIP",
        "name": "SVIP",
        "svg_path": "M10 10 H120 V80 H10 Z",
        "sort_order": 1
      }
    ]
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

**Response với `format=svg` hoặc `format=image`**

- Có thể trả `302` tới CDN asset.
- Header asset nên là `Cache-Control: public, max-age=31536000, immutable`.

---

### 6.5. `GET /concerts/{concert_id}/ticket-types`

Trả danh sách loại vé public cho concert.

**Query parameters**

| Tên | Kiểu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `include_closed` | boolean | Không | Mặc định `false`; nếu `true` vẫn trả loại vé đã `CLOSED/SOLD_OUT` để UI hiển thị. |

**Response `200`**

```json
{
  "data": [
    {
      "id": "tkt_01JX9Q5A",
      "concert_id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
      "seat_zone_id": "zon_01JX9Q4A",
      "zone_code": "SVIP",
      "name": "SVIP",
      "description": "Khu vực gần sân khấu.",
      "price": {
        "amount": 4500000,
        "currency": "VND"
      },
      "max_per_user": 2,
      "sale_start_at": "2026-07-01T03:00:00Z",
      "sale_end_at": "2026-08-09T17:00:00Z",
      "status": "ON_SALE"
    }
  ],
  "meta": {
    "request_id": "req_01JX9Q6N4E"
  }
}
```

**Cache**

- Redis key: `catalog:ticket-types:{concert_id}:{include_closed}`
- TTL: `5-30 phút`
- Invalidate khi admin tạo/cập nhật ticket type hoặc publish/cancel concert.

---

### 6.6. `GET /concerts/{concert_id}/inventory`

Trả số vé còn lại gần thời gian thực. Endpoint này đọc Redis ở đường nóng, không dùng PostgreSQL để phục vụ request bình thường.

**Response `200`**

```json
{
  "data": {
    "concert_id": "crt_01JX9Q2M5P7KZ3R4N8Y6",
    "as_of": "2026-05-30T10:15:30Z",
    "items": [
      {
        "ticket_type_id": "tkt_01JX9Q5A",
        "seat_zone_id": "zon_01JX9Q4A",
        "zone_code": "SVIP",
        "available_quantity": 118,
        "status": "ON_SALE",
        "display_status": "AVAILABLE"
      },
      {
        "ticket_type_id": "tkt_01JX9Q5B",
        "seat_zone_id": "zon_01JX9Q4B",
        "zone_code": "CAT1",
        "available_quantity": 0,
        "status": "SOLD_OUT",
        "display_status": "SOLD_OUT"
      }
    ]
  },
  "meta": {
    "request_id": "req_01JX9Q6N4E",
    "consistency": "EVENTUAL",
    "cache_ttl_seconds": 5
  }
}
```

**Redis model gợi ý**

```text
inventory:concert:{concert_id}
  ticket_type:{ticket_type_id}:available_quantity_computed
  ticket_type:{ticket_type_id}:status
  ticket_type:{ticket_type_id}:updated_at
```

Hoặc dùng Redis Hash:

```text
HGETALL inventory:concert:{concert_id}
```

**Display status**

| Điều kiện | `display_status` |
| --- | --- |
| `available_quantity > low_stock_threshold` và đang mở bán | `AVAILABLE` |
| `0 < available_quantity <= low_stock_threshold` | `LOW_STOCK` |
| `available_quantity = 0` hoặc `status = SOLD_OUT` | `SOLD_OUT` |
| Ngoài sale window hoặc `status = CLOSED` | `CLOSED` |
| Redis fallback snapshot cũ | `UPDATING` |

**Kịch bản lỗi**

| Trường hợp | HTTP | Code | Hành vi |
| --- | --- | --- | --- |
| Redis inventory lỗi nhưng fallback còn snapshot | `200` | Không có | Trả snapshot và set `display_status = UPDATING`. |
| Redis lỗi và circuit breaker mở | `503` | `INVENTORY_UNAVAILABLE` | UI hiển thị "Đang cập nhật". |
| Concert không tồn tại hoặc chưa published | `404` | `CONCERT_NOT_FOUND` | Không lộ concert draft cho guest. |

**Rate limit**

- Key: `rate:inventory:{ip}:{concert_id}`
- Gợi ý: `120 requests/phút`
- Response khi vượt ngưỡng: `429 RATE_LIMITED` kèm `Retry-After`.

---

## 7. Admin API chi tiết

### Quy ước chung cho 9 endpoint

- Auth: `Authorization: Bearer <access_token>`, role `ORGANIZER` hoặc `ADMIN`; RBAC tại mục 4 áp dụng cho mọi endpoint. Response Admin có `Cache-Control: no-store`.
- JSON request/response dùng `snake_case`; giữ tên query `sortBy`, `sortOrder` theo controller hiện có. Path ID là UUID. Datetime là RFC 3339 có `Z` hoặc offset; so sánh theo thời điểm tuyệt đối, response UTC (`Z`).
- Số nguyên nằm trong miền INT PostgreSQL. Chuỗi bắt buộc không được blank. Enum phân biệt hoa/thường, dùng các giá trị viết hoa đã liệt kê. Giá không làm tròn ngầm: từ 0 đến 9999999999.99, tối đa 2 chữ số thập phân.
- Từ chối field không hỗ trợ hoặc field server quản lý bằng `400 INVALID_REQUEST_FIELD`. JSON sai cú pháp/kiểu hoặc UUID/datetime sai định dạng trả `400 INVALID_REQUEST`; thiếu field bắt buộc, null không hợp lệ hoặc vi phạm giới hạn trả `422 VALIDATION_ERROR`.
- PATCH dùng JSON object (`application/json`): field vắng mặt giữ nguyên; `null` chỉ xóa các field nullable trong bảng tương ứng. `{}` là no-op `200`, không đổi `updated_at` hoặc ghi audit/invalidate; vẫn kiểm tra quyền và trạng thái cho phép sửa. Kiểm tra toàn bộ dữ liệu sau merge, kể cả khi chỉ sửa một đầu mốc thời gian.
- ID, quan hệ cha, organizer, audit timestamps và inventory counters do server quản lý. Không đổi slug concert, code zone, seat zone của ticket type hoặc currency sau tạo.
- Concert, zone và ticket type chỉ tạo/sửa cấu hình khi concert cha là `DRAFT` hoặc `PUBLISHED`. Concert hủy hoặc `COMPLETED` chỉ đọc; vi phạm trả `409 INVALID_CONCERT_STATE`.
- POST tạo resource không có cơ chế replay/`Idempotency-Key`: retry có thể gặp `409` do unique key; không bảo đảm trả lại response lần đầu. PATCH cùng payload gán lại giá trị, không cộng dồn; vẫn validate theo trạng thái mới nhất và không bảo đảm response/timestamp giống lần trước. Không thêm ETag/If-Match.
- Mọi endpoint áp dụng lỗi chung: `400 INVALID_REQUEST`, `401 UNAUTHORIZED`, `403 FORBIDDEN`, `429 RATE_LIMITED`; endpoint có body thêm `400 INVALID_REQUEST_FIELD`, `422 VALIDATION_ERROR`. Lỗi riêng được ghi bên dưới, tra ý nghĩa tại mục 10. Lỗi bất ngờ trả `500 INTERNAL_SERVER_ERROR`, không lộ SQL/stack trace.

**Response resources**

Các response mẫu dưới đây là đầy đủ field của resource, không trả JPA entity. Concert trả các field create cộng `id`, `organizer_id`, `organizer_name` (từ `users.full_name`), `status`, `created_at`, `updated_at`. Zone trả các field create cộng `id`, `concert_id`. Ticket type trả các field create cộng `id`, `concert_id`, `zone_code`, các inventory counters và `status`. Field nullable được trả rõ `null`. Zone/ticket type không trả audit timestamps trong contract này, dù DB có lưu.

### 7.1. `GET /admin/concerts`

Danh sách mọi trạng thái theo quyền, bao gồm draft và concert đã hủy. Không có request body.

| Query | Kiểu | Bắt buộc / mặc định | Quy tắc |
| --- | --- | --- | --- |
| `q` | string | Không | Trim; rỗng coi như không lọc; tìm chuỗi con không phân biệt hoa/thường trong title hoặc artist_name. `%` và `_` được coi là ký tự tìm kiếm thường. |
| `status` | enum | Không | `DRAFT`, `PUBLISHED`, `CANCELED`, `COMPLETED`; nhận alias `CANCELLED`. Không gửi thì không lọc trạng thái. |
| `page` | integer | Không / 0 | >= 0, phân trang từ 0. |
| `size` | integer | Không / 20 | 1–100. |
| `sortBy` | string | Không / `createdAt` | Allowlist: `createdAt`, `startsAt`, `title`. |
| `sortOrder` | string | Không / `desc` | `asc` hoặc `desc`. Thêm `id ASC` làm tie-breaker. |

Không hỗ trợ `venue_id`, `from`, `to`, cursor/limit hoặc query ngoài bảng; trả `400 INVALID_QUERY`. Không có kết quả hoặc page vượt cuối trả `200`, `data: []`, `has_more: false`; total_items/total_pages tính theo cùng filter và ownership. Tổng 0 có total_pages 0. Pagination theo page không bảo đảm snapshot giữa các lần gọi khi dữ liệu thay đổi.

**Response `200`**

```json
{
  "data": [
    {
      "id": "11111111-1111-4111-8111-111111111111",
      "organizer_id": "22222222-2222-4222-8222-222222222222",
      "organizer_name": "Nguyễn An",
      "title": "Live Concert",
      "slug": "live-concert",
      "venue": "Sân vận động Mỹ Đình",
      "description": null,
      "artist_name": "Various Artists",
      "artist_bio": null,
      "starts_at": "2026-12-10T12:00:00Z",
      "ends_at": "2026-12-10T16:00:00Z",
      "status": "DRAFT",
      "cover_image_url": null,
      "seat_map_url": null,
      "created_at": "2026-10-01T03:00:00Z",
      "updated_at": "2026-10-01T03:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "page_size": 20,
    "total_items": 1,
    "total_pages": 1,
    "has_more": false
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `400 INVALID_QUERY`, `400 INVALID_SORT`, `400 INVALID_STATUS`. GET có thể retry, không có side effect.

### 7.2. `POST /admin/concerts`

Tạo concert `DRAFT`; server lấy organizer từ caller, kể cả ADMIN.

| Field | Kiểu | Bắt buộc / mặc định | Validation |
| --- | --- | --- | --- |
| `title`, `slug`, `venue`, `artist_name` | string | Có | Không blank, tối đa 255 ký tự mỗi field; slug unique toàn hệ thống. |
| `starts_at`, `ends_at` | datetime | Có | `ends_at > starts_at`. |
| `description`, `artist_bio` | string hoặc null | Không / null | TEXT nullable. |
| `cover_image_url`, `seat_map_url` | string hoặc null | Không / null | TEXT nullable, URL tài nguyên; không tạo API upload trong scope này. |

**Request**

```json
{
  "title": "Live Concert",
  "slug": "live-concert",
  "venue": "Sân vận động Mỹ Đình",
  "artist_name": "Various Artists",
  "starts_at": "2026-12-10T12:00:00Z",
  "ends_at": "2026-12-10T16:00:00Z"
}
```

**Response `201`**

```json
{
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "organizer_id": "22222222-2222-4222-8222-222222222222",
    "organizer_name": "Nguyễn An",
    "title": "Live Concert",
    "slug": "live-concert",
    "venue": "Sân vận động Mỹ Đình",
    "description": null,
    "artist_name": "Various Artists",
    "artist_bio": null,
    "starts_at": "2026-12-10T12:00:00Z",
    "ends_at": "2026-12-10T16:00:00Z",
    "status": "DRAFT",
    "cover_image_url": null,
    "seat_map_url": null,
    "created_at": "2026-10-01T03:00:00Z",
    "updated_at": "2026-10-01T03:00:00Z"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `409 SLUG_ALREADY_EXISTS`, `422 INVALID_CONCERT_TIME_RANGE`. `status`, `organizer_id` không được nhận trong request. Retry theo quy tắc POST tạo resource; không bổ sung GET detail hoặc Location trỏ tới endpoint chưa có.

### 7.3. `PATCH /admin/concerts/{concert_id}`

| Field | Kiểu | Bắt buộc / null | Validation |
| --- | --- | --- | --- |
| `title`, `venue`, `artist_name` | string | Không / không cho null | Không blank, tối đa 255 ký tự. |
| `starts_at`, `ends_at` | datetime | Không / không cho null | Sau merge: ends_at > starts_at. |
| `description`, `artist_bio`, `cover_image_url`, `seat_map_url` | string hoặc null | Không / cho phép xóa | Như bảng create. |

**Request**

```json
{
  "title": "Live Concert 2026",
  "artist_bio": null
}
```

**Response `200`**

```json
{
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "organizer_id": "22222222-2222-4222-8222-222222222222",
    "organizer_name": "Nguyễn An",
    "title": "Live Concert 2026",
    "slug": "live-concert",
    "venue": "Sân vận động Mỹ Đình",
    "description": null,
    "artist_name": "Various Artists",
    "artist_bio": null,
    "starts_at": "2026-12-10T12:00:00Z",
    "ends_at": "2026-12-10T16:00:00Z",
    "status": "DRAFT",
    "cover_image_url": null,
    "seat_map_url": null,
    "created_at": "2026-10-01T03:00:00Z",
    "updated_at": "2026-10-01T03:10:00Z"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 CONCERT_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `422 INVALID_CONCERT_TIME_RANGE`. Không nhận `slug`, `status` hoặc organizer. Retry theo quy tắc PATCH; cập nhật dữ liệu published phải invalidate cache sau commit.

### 7.4. `POST /admin/concerts/{concert_id}/publish`

| Request | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `concert_id` (path UUID) | Có | Concert tồn tại và caller có quyền. |
| Body | Không | Không có payload; chấp nhận body vắng mặt hoặc `{}`. Field khác trả `400 INVALID_REQUEST_FIELD`. |

- Chỉ chuyển `DRAFT → PUBLISHED`. Yêu cầu venue không blank, thời gian concert hợp lệ, ít nhất một zone và một ticket type.
- Tổng total_quantity của tất cả ticket type trong từng zone (mọi status) không vượt capacity; các sale window hợp lệ. Không tự thêm điều kiện sale_end_at trước starts_at hoặc quantity > 0 ngoài schema.
- Cùng transaction, chuyển ticket type `DRAFT → ON_SALE`, giữ các status khác. `ON_SALE` không bỏ qua kiểm tra sale window và trạng thái concert trong luồng bán vé.
- Đã `PUBLISHED`: trả `200` resource hiện tại ngay sau kiểm tra quyền, không chuyển ticket type DRAFT mới tạo, không lặp audit/cache side effects. Ticket type tạo sau publish vẫn DRAFT, có thể đổi status qua PATCH.
- Hủy hoặc COMPLETED: `409 INVALID_CONCERT_STATE`. Không có endpoint đưa concert về draft hoặc hoàn tất concert trong scope này.

**Response `200`**

```json
{
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "organizer_id": "22222222-2222-4222-8222-222222222222",
    "organizer_name": "Nguyễn An",
    "title": "Live Concert",
    "slug": "live-concert",
    "venue": "Sân vận động Mỹ Đình",
    "description": null,
    "artist_name": "Various Artists",
    "artist_bio": null,
    "starts_at": "2026-12-10T12:00:00Z",
    "ends_at": "2026-12-10T16:00:00Z",
    "status": "PUBLISHED",
    "cover_image_url": null,
    "seat_map_url": null,
    "created_at": "2026-10-01T03:00:00Z",
    "updated_at": "2026-10-01T03:20:00Z"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 CONCERT_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `422 CANNOT_PUBLISH_CONCERT` (detail mô tả điều kiện thiếu hoặc không hợp lệ). Retry publish an toàn theo trạng thái như trên.

### 7.5. `POST /admin/concerts/{concert_id}/cancel`

| Field | Kiểu | Bắt buộc / mặc định | Quy tắc |
| --- | --- | --- | --- |
| `reason` | string hoặc null | Không / null | Nội dung phục vụ audit; không lưu thành cột trên concerts. |

Chấp nhận không có body hoặc `{}`. Chuyển `DRAFT`/`PUBLISHED → CANCELED`; `COMPLETED` trả `409`. Nếu đã hủy (kể cả DB là CANCELLED), trả `200` resource chuẩn hóa, không lặp side effect và không ghi đè lý do audit lần đầu. Không xóa vật lý, không tự refund/cancel order, không thay đổi status ticket type hoặc held/sold. Payment/Ticketing chịu trách nhiệm xử lý giao dịch liên quan.

**Request**

```json
{
  "reason": "Hủy sự kiện do lý do vận hành."
}
```

**Response `200`**

```json
{
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "organizer_id": "22222222-2222-4222-8222-222222222222",
    "organizer_name": "Nguyễn An",
    "title": "Live Concert",
    "slug": "live-concert",
    "venue": "Sân vận động Mỹ Đình",
    "description": null,
    "artist_name": "Various Artists",
    "artist_bio": null,
    "starts_at": "2026-12-10T12:00:00Z",
    "ends_at": "2026-12-10T16:00:00Z",
    "status": "CANCELED",
    "cover_image_url": null,
    "seat_map_url": null,
    "created_at": "2026-10-01T03:00:00Z",
    "updated_at": "2026-10-01T03:30:00Z"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 CONCERT_NOT_FOUND`, `409 INVALID_CONCERT_STATE`. Retry cancel an toàn theo trạng thái như trên. Hủy concert phải invalidate cache liên quan sau commit.

### 7.6. `POST /admin/concerts/{concert_id}/seat-zones`

| Field | Kiểu | Bắt buộc / mặc định | Validation |
| --- | --- | --- | --- |
| `code` | string | Có | Không blank, tối đa 50 ký tự; trim và uppercase trước khi kiểm tra unique `(concert_id, code)` và lưu. |
| `name` | string | Có | Không blank, tối đa 100 ký tự. |
| `capacity` | integer | Có | > 0. |
| `description`, `svg_path` | string hoặc null | Không / null | TEXT nullable. |
| `sort_order` | integer | Không / 0 | Cho phép âm theo schema. |

Không so sánh capacity với venue vì venue chỉ là string.

**Request**

```json
{
  "code": "SVIP",
  "name": "SVIP",
  "capacity": 200
}
```

**Response `201`**

```json
{
  "data": {
    "id": "33333333-3333-4333-8333-333333333333",
    "concert_id": "11111111-1111-4111-8111-111111111111",
    "code": "SVIP",
    "name": "SVIP",
    "description": null,
    "capacity": 200,
    "svg_path": null,
    "sort_order": 0
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 CONCERT_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `409 SEAT_ZONE_CODE_ALREADY_EXISTS`. Retry theo quy tắc POST tạo resource.

### 7.7. `PATCH /admin/seat-zones/{seat_zone_id}`

| Field | Kiểu | Bắt buộc / null | Validation |
| --- | --- | --- | --- |
| `name` | string | Không / không cho null | Không blank, tối đa 100 ký tự. |
| `capacity` | integer | Không / không cho null | > 0 và >= tổng total_quantity mọi ticket type trong zone. |
| `description`, `svg_path` | string hoặc null | Không / cho phép xóa | TEXT nullable. |
| `sort_order` | integer | Không / không cho null | Cho phép âm. |

Không nhận code hoặc concert_id. Ownership và lifecycle kiểm tra trên concert cha của zone.

**Request**

```json
{
  "capacity": 250,
  "sort_order": 1
}
```

**Response `200`**

```json
{
  "data": {
    "id": "33333333-3333-4333-8333-333333333333",
    "concert_id": "11111111-1111-4111-8111-111111111111",
    "code": "SVIP",
    "name": "SVIP",
    "description": null,
    "capacity": 250,
    "svg_path": null,
    "sort_order": 1
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 SEAT_ZONE_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `422 ZONE_CAPACITY_EXCEEDED`. Retry theo quy tắc PATCH.

### 7.8. `POST /admin/concerts/{concert_id}/ticket-types`

| Field | Kiểu | Bắt buộc / mặc định | Validation |
| --- | --- | --- | --- |
| `seat_zone_id` | UUID | Có | Zone phải thuộc concert trong path. |
| `name` | string | Có | Không blank, tối đa 100 ký tự; unique `(concert_id, name)`. |
| `description` | string hoặc null | Không / null | TEXT nullable. |
| `price` | number | Có | 0–9999999999.99, tối đa 2 chữ số thập phân; không phải object amount/currency. |
| `currency` | string | Không / `VND` | Đúng 3 chữ cái in hoa; không tự chuyển đổi tiền tệ. |
| `total_quantity` | integer | Có | >= 0; tổng quantity các ticket type trong zone sau thêm không vượt capacity. |
| `max_per_user` | integer | Có | > 0. |
| `sale_start_at`, `sale_end_at` | datetime | Có | sale_end_at > sale_start_at. |

Server đặt status DRAFT, held_quantity = sold_quantity = 0. Không nhận status hoặc counters trong request. Zone không tồn tại hoặc thuộc concert khác đều trả `404 SEAT_ZONE_NOT_FOUND` sau khi kiểm tra quyền concert đích.

**Request**

```json
{
  "seat_zone_id": "33333333-3333-4333-8333-333333333333",
  "name": "SVIP",
  "price": 4500000,
  "currency": "VND",
  "total_quantity": 200,
  "max_per_user": 2,
  "sale_start_at": "2026-11-01T03:00:00Z",
  "sale_end_at": "2026-12-09T17:00:00Z"
}
```

**Response `201`**

```json
{
  "data": {
    "id": "44444444-4444-4444-8444-444444444444",
    "concert_id": "11111111-1111-4111-8111-111111111111",
    "seat_zone_id": "33333333-3333-4333-8333-333333333333",
    "zone_code": "SVIP",
    "name": "SVIP",
    "description": null,
    "price": 4500000,
    "currency": "VND",
    "total_quantity": 200,
    "held_quantity": 0,
    "sold_quantity": 0,
    "available_quantity": 200,
    "max_per_user": 2,
    "sale_start_at": "2026-11-01T03:00:00Z",
    "sale_end_at": "2026-12-09T17:00:00Z",
    "status": "DRAFT"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 CONCERT_NOT_FOUND`, `404 SEAT_ZONE_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `409 TICKET_TYPE_NAME_ALREADY_EXISTS`, `422 INVALID_SALE_WINDOW`, `422 ZONE_CAPACITY_EXCEEDED`. Retry theo quy tắc POST tạo resource.

### 7.9. `PATCH /admin/ticket-types/{ticket_type_id}`

| Field | Kiểu | Bắt buộc / null | Validation |
| --- | --- | --- | --- |
| `name` | string | Không / không cho null | Không blank, tối đa 100 ký tự; unique trong concert, loại trừ chính resource. |
| `description` | string hoặc null | Không / cho phép xóa | TEXT nullable. |
| `price` | number | Không / không cho null | Cùng giới hạn create. |
| `total_quantity` | integer | Không / không cho null | >= 0, >= held + sold; tổng quantity mọi ticket type trong zone không vượt capacity. |
| `max_per_user` | integer | Không / không cho null | > 0. |
| `sale_start_at`, `sale_end_at` | datetime | Không / không cho null | Sau merge: sale_end_at > sale_start_at. |
| `status` | enum | Không / không cho null | `DRAFT`, `ACTIVE`, `ON_SALE`, `SUSPENDED`, `CLOSED`, `SOLD_OUT`. |

Cho phép sửa total_quantity cả khi PUBLISHED; không yêu cầu API inventory adjustment riêng. Không nhận currency, seat_zone_id, concert_id, held_quantity, sold_quantity hoặc available_quantity. Cấu hình status không thay đổi counters; v1 không thêm state machine riêng cho ticket type. Giới hạn mua mới dùng max_per_user mới, không thu hồi hoặc sửa vé/order hiện hữu khi giảm giới hạn. Giá mới không sửa giá đã chốt trong order_items.

**Request**

```json
{
  "total_quantity": 180,
  "status": "ON_SALE"
}
```

**Response `200`**

```json
{
  "data": {
    "id": "44444444-4444-4444-8444-444444444444",
    "concert_id": "11111111-1111-4111-8111-111111111111",
    "seat_zone_id": "33333333-3333-4333-8333-333333333333",
    "zone_code": "SVIP",
    "name": "SVIP",
    "description": null,
    "price": 4500000,
    "currency": "VND",
    "total_quantity": 180,
    "held_quantity": 0,
    "sold_quantity": 0,
    "available_quantity": 180,
    "max_per_user": 2,
    "sale_start_at": "2026-11-01T03:00:00Z",
    "sale_end_at": "2026-12-09T17:00:00Z",
    "status": "ON_SALE"
  },
  "meta": {
    "request_id": "req_example"
  }
}
```

**Lỗi riêng:** `404 TICKET_TYPE_NOT_FOUND`, `409 INVALID_CONCERT_STATE`, `409 TICKET_TYPE_NAME_ALREADY_EXISTS`, `422 VALIDATION_ERROR` (enum/body không hợp lệ), `422 INVALID_SALE_WINDOW`, `422 INVALID_QUANTITY`, `422 ZONE_CAPACITY_EXCEEDED`. Retry theo quy tắc PATCH.

### Giao dịch, audit và cache của Admin

- Ownership, lifecycle, merge, validation và mutation phải nằm trong transaction. Đồng bộ các write cùng concert để publish/cancel không race với chỉnh sửa cấu hình; kiểm tra capacity phải nguyên tử với mọi thay đổi zone/ticket quantity.
- Khi đổi total_quantity, serialize/lock với luồng hold/sell/release của Ticketing và kiểm tra held/sold mới nhất trong DB. Không dùng Redis để quyết định invariant; hai request đồng thời không được cùng vượt capacity. Unique/FK/CHECK trong DB là hàng rào cuối, lỗi constraint dự kiến phải map về mã lỗi có nghĩa.
- Audit ghi actor, resource, action và before/after; cancel ghi reason. No-op PATCH và publish/cancel lặp cùng trạng thái không tạo side effect mới.
- Sau commit mới invalidate cache tại mục 8.2. Không trừ/cộng lại inventory trên Redis bằng delta khi retry. Cache failure sau commit không được biến write đã thành công thành rollback giả; ghi lỗi và retry invalidation qua cơ chế vận hành hiện có. Không hứa warm cache đồng bộ hoặc thêm hạ tầng mới trong scope này.

### Chênh lệch backend cần triển khai sau

Đây là backlog để đạt contract đích, không phải thay đổi Java trong tài liệu này:

| Hiện trạng đã inspect | Contract cần đạt |
| --- | --- |
| Controller nhận sort/page trực tiếp, chưa giới hạn size/allowlist/tie-breaker. | Validate query theo 7.1, sort ổn định và xử lý status alias. |
| Entity chỉ có CANCELED, DB cho cả CANCELLED. | Đọc được alias cũ và chuẩn hóa response/filter, không chỉ gọi enum valueOf. |
| DTO/entity dùng LocalDateTime. | Bảo toàn offset/instant khi đọc ghi TIMESTAMPTZ, response UTC. |
| PATCH bỏ qua null, chưa phân biệt thiếu field; một số blank bị bỏ qua. | Theo dõi field presence, clear nullable, từ chối blank/null/field không hỗ trợ đúng contract. |
| Service chưa chặn lifecycle; publish/cancel luôn thực hiện write. | State guards, xử lý retry không lặp side effect, terminal chỉ đọc. |
| Capacity chủ yếu kiểm tra tại publish; kiểm tra inventory khi PATCH chưa đủ chống race. | Validate khi tạo/sửa và serialize transaction với các writer inventory. |
| DTO total_quantity yêu cầu >= 1. | Cho phép 0 theo schema; giới hạn precision price và validation còn thiếu. |
| Một số lỗi business/unique trả 400; handler dùng ErrorResponse riêng. | HTTP/code ở mục 10 và Problem Detail cho Admin, không đổi contract public trong task này. |
| Chưa có kiểm tra trùng tên khi PATCH; code zone kiểm tra trước uppercase. | Unique check sau normalization và map lỗi DB để xử lý race. |
| Response dùng DTO hiện có; invalidate được gọi trong service trước commit; audit chủ yếu log. | Hoàn thiện null/time serialization, audit có cấu trúc và invalidation sau commit. |

---

## 8. Cache, invalidation và graceful degradation

### 8.1. Cache keys

| Dữ liệu | Key gợi ý | TTL |
| --- | --- | --- |
| Concert list | `catalog:list:{hash(query)}` | 5-30 phút |
| Concert detail | `catalog:concert:{concert_id}` | 1-24 giờ |
| Metadata | `catalog:metadata:{concert_id}` | 24 giờ |
| Seat map metadata | `catalog:seat-map:{concert_id}` | 24 giờ |
| Ticket types | `catalog:ticket-types:{concert_id}:{include_closed}` | 5-30 phút |
| Inventory | `inventory:concert:{concert_id}` | 5-10 giây hoặc update theo event |

### 8.2. Invalidation rules

| Hành động admin | Cache cần xóa |
| --- | --- |
| Tạo/cập nhật concert | `concerts:{id}*`, `concerts:all*`. |
| Publish/cancel concert | `concerts:{id}*`, `concerts:all*` (bao gồm metadata, seat map, ticket types và inventory). |
| Tạo/cập nhật seat zone | `concerts:{id}*`. |
| Tạo/cập nhật ticket type | `concerts:{id}*`, `concerts:all*` (bao gồm detail/list có giá vé). |
| Active artist bio thay đổi | `concerts:{id}*`. |

### 8.3. Graceful degradation

- Metadata CDN origin lỗi: CDN dùng `stale-if-error` để trả snapshot cũ.
- Redis inventory lỗi có snapshot: trả `200` với `display_status = UPDATING`.
- Redis inventory lỗi không snapshot: trả `503 INVENTORY_UNAVAILABLE`.
- PostgreSQL không được query trên đường nóng inventory khi Redis đang lỗi hàng loạt; tránh làm sập source of truth.

---

## 9. Rate limiting

| Nhóm endpoint | Key | Ngưỡng gợi ý |
| --- | --- | --- |
| `GET /concerts` | IP | `200 requests/phút` |
| `GET /concerts/{id}/metadata` | IP + concert | CDN/WAF xử lý là chính |
| `GET /concerts/{id}/seat-map` | IP + concert | CDN/WAF xử lý là chính |
| `GET /concerts/{id}/inventory` | IP + concert | `120 requests/phút` |
| Admin write | user | `60 requests/phút` |

Response `429`:

```json
{
  "type": "https://api.ticketbox.vn/errors/rate-limited",
  "title": "Tần suất truy cập quá cao",
  "status": 429,
  "code": "RATE_LIMITED",
  "detail": "Vui lòng đợi trước khi thử lại.",
  "instance": "/v1/concerts/crt_01JX9Q2M5P7KZ3R4N8Y6/inventory",
  "request_id": "req_01JX9Q6N4G"
}
```

Header:

```http
Retry-After: 30
```

---

## 10. Error catalog

Các lỗi Admin dùng `application/problem+json`, không bọc trong `data`. `code` ổn định cho client; `detail` mô tả lỗi, không để client phân tích text. Giữ các lỗi public hiện có trong bảng; ví dụ public mục 6 không được sửa.

```json
{
  "type": "https://api.ticketbox.vn/errors/zone-capacity-exceeded",
  "title": "Vượt sức chứa khu vực",
  "status": 422,
  "code": "ZONE_CAPACITY_EXCEEDED",
  "detail": "Tổng số vé cấu hình trong zone vượt capacity.",
  "instance": "/v1/admin/ticket-types/44444444-4444-4444-8444-444444444444",
  "request_id": "req_example"
}
```

| HTTP | Code | Khi nào xảy ra |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | JSON/kiểu dữ liệu/UUID/datetime sai định dạng. |
| `400` | `INVALID_REQUEST_FIELD` | Field body không hỗ trợ, bất biến hoặc do server quản lý. |
| `400` | `INVALID_QUERY` | Query không hỗ trợ, sai kiểu hoặc vượt giới hạn. |
| `400` | `INVALID_SORT` | sortBy/sortOrder ngoài allowlist. |
| `400` | `INVALID_STATUS` | Filter status concert không hợp lệ. |
| `401` | `UNAUTHORIZED` | Thiếu access token hoặc token không hợp lệ/hết hạn. |
| `403` | `FORBIDDEN` | Role không đủ quyền hoặc ORGANIZER không sở hữu concert cha. |
| `404` | `CONCERT_NOT_FOUND` | Concert không tồn tại; với public giữ quy tắc ẩn concert chưa công bố. |
| `404` | `SEAT_ZONE_NOT_FOUND` | Zone không tồn tại hoặc không thuộc concert đích khi tạo vé. |
| `404` | `TICKET_TYPE_NOT_FOUND` | Ticket type không tồn tại. |
| `409` | `SLUG_ALREADY_EXISTS` | Slug concert trùng. |
| `409` | `SEAT_ZONE_CODE_ALREADY_EXISTS` | Code zone trùng sau chuẩn hóa trong concert. |
| `409` | `TICKET_TYPE_NAME_ALREADY_EXISTS` | Tên ticket type trùng trong concert. |
| `409` | `INVALID_CONCERT_STATE` | Mutation không được phép ở trạng thái concert hiện tại. |
| `422` | `VALIDATION_ERROR` | Thiếu field, null/blank không hợp lệ, sai giới hạn hoặc enum body không hợp lệ. |
| `422` | `INVALID_SALE_WINDOW` | sale_end_at <= sale_start_at sau merge. |
| `422` | `INVALID_CONCERT_TIME_RANGE` | ends_at <= starts_at sau merge. |
| `422` | `INVALID_QUANTITY` | total_quantity mới dưới held_quantity + sold_quantity. |
| `422` | `ZONE_CAPACITY_EXCEEDED` | Tổng total_quantity trong zone vượt capacity, kể cả khi giảm capacity. |
| `422` | `CANNOT_PUBLISH_CONCERT` | Không đủ điều kiện publish ở mục 7.4. |
| `429` | `RATE_LIMITED` | Vượt rate limit, kèm Retry-After. |
| `500` | `INTERNAL_SERVER_ERROR` | Lỗi bất ngờ, không lộ chi tiết hạ tầng. |
| `503` | `INVENTORY_UNAVAILABLE` | Lỗi public inventory theo mục 6.6. |

---

## 11. Quy tắc triển khai backend

1. Public endpoint không trả concert `DRAFT`.
2. Không dùng Redis inventory để quyết định bán vé. Ticketing Module phải lock PostgreSQL.
3. Admin mutation ghi audit với `before_data`, `after_data`; no-op/retry áp dụng ngoại lệ tại mục 7.
4. Admin update dữ liệu public phải invalidate Redis sau commit theo mục 8.2.
5. Metadata endpoint nên dùng SingleFlight khi cache miss.
6. Inventory endpoint phải ưu tiên Redis và snapshot fallback; không biến PostgreSQL thành fallback nóng.
7. Các response public phải có `ETag` nếu cache được.
8. Không trả stack trace hoặc thông tin hạ tầng nội bộ trong lỗi.

---

## 12. Acceptance criteria

- `GET /concerts` chỉ trả concert `PUBLISHED`, có filter/pagination ổn định.
- `GET /concerts/{id}/metadata` trả đủ concert, venue, seat zones, ticket types, active artist bio và asset URLs.
- Metadata public có header cache dài, hỗ trợ `ETag`, `stale-while-revalidate`, `stale-if-error`.
- `GET /concerts/{id}/inventory` trả số vé còn lại từ Redis với `consistency = EVENTUAL`.
- Redis lỗi có fallback snapshot thì UI vẫn hiển thị được trạng thái "Đang cập nhật".

### Acceptance criteria Admin (contract đích)

- Chỉ có đúng 9 endpoint tại mục 5.2; không có CRUD venue, DELETE concert, GET admin detail, upload hoặc API nâng role trong phạm vi này.
- ORGANIZER tạo concert với organizer_id của mình; list chỉ gồm dữ liệu mình sở hữu, tổng pagination không lộ concert khác. ADMIN liệt kê/sửa mọi concert; ADMIN tạo concert vẫn là chủ sở hữu.
- AUDIENCE/CHECKER bị 403; thiếu/sai token bị 401; truy cập resource khác chủ bị 403; ID không tồn tại trả 404 đúng resource. PATCH zone/ticket kiểm tra quyền qua concert cha.
- Request/response Admin dùng UUID, venue string, price number/currency riêng, RFC 3339 có offset và response UTC; không có published_at/cancelled_at. Alias CANCELLED được đọc/lọc và trả CANCELED.
- List kiểm tra page/size/sort/status, size tối đa 100, tie-breaker ổn định; kiểm tra trang rỗng và nhiều concert có cùng giá trị sort.
- Validation khớp SQL: độ dài chuỗi, nullable, unique slug/code/name, FK zone cùng concert, giá precision/scale, quantity 0 hợp lệ, max_per_user dương và time range hợp lệ. Kiểm tra cả trùng tên khi PATCH và hai request tạo trùng đồng thời.
- PATCH giữ field vắng mặt, xóa nullable bằng null, chặn null bắt buộc/field bất biến/counters. Kiểm tra cập nhật riêng từng đầu mốc thời gian và request rỗng.
- DRAFT/PUBLISHED được sửa; CANCELED/CANCELLED/COMPLETED không được sửa cấu hình. Publish thiếu zone/ticket hoặc vượt capacity bị 422; publish từ terminal bị 409. Publish/cancel lặp trả 200 không lặp audit hoặc thay đổi timestamp.
- Tổng vé theo zone không vượt capacity khi tạo/sửa ticket hoặc giảm capacity; kiểm tra nhiều ticket type cộng dồn, cả status không mở bán. total_quantity không xuống dưới held + sold; kiểm tra race giữa PATCH quantity, hold/sell và chỉnh capacity.
- Publish chỉ chuyển ticket DRAFT sang ON_SALE trong lần chuyển trạng thái; retry không kích hoạt ticket mới. Cancel chỉ đổi concert, không tự hoàn tiền hoặc chỉnh inventory counters.
- Mutation thực tế có audit và invalidate sau commit; rollback không invalidate, retry/no-op không tạo side effect trùng. Lỗi cache sau commit có log/retry invalidation.
- Các ví dụ JSON parse được; bảng request, response, error và endpoint thống nhất. Mục 5.1 và toàn bộ mục 6 public giữ nguyên; diff của task chỉ sửa tài liệu này.
