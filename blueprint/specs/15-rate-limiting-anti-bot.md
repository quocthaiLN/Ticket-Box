# Đặc tả: Rate Limiting & Anti-bot

## 1. Mô tả

Đặc tả kiểm soát tải đột biến và chống client spam/scalper bot ở API Gateway/middleware.

## 2. Actor / Thành phần tham gia

- Client
- Nginx (IP-level limit)
- Bucket4j Rate Limit Filter (Spring Boot, per-user/route)
- Redis
- Backend Modules

## 3. Bảng dữ liệu liên quan

- `audit_logs`

## 4. Luồng chính

1. Request đi qua Nginx trước; Nginx áp giới hạn theo IP (leaky bucket) để chặn flood/DDoS.
2. Request qua Nginx vào Spring Boot; `RateLimitFilter` (Bucket4j) xác định key theo user id, route và concert/ticket type nếu cần.
3. Filter dùng token bucket (backend Redis) để kiểm tra số request còn lại.
4. Nếu còn token, request được chuyển tiếp vào Controller backend.
5. Nếu hết token, trả HTTP 429 kèm `Retry-After`.
6. Các endpoint nhạy cảm như hold/order/payment dùng ngưỡng thấp hơn catalog.
7. Hành vi bất thường có thể ghi `audit_logs` hoặc security log để phân tích.

## 5. Kịch bản lỗi

- Redis rate limit lỗi: fail-closed cho endpoint checkout/payment, fail-open có kiểm soát cho catalog tùy chính sách.
- User dùng nhiều IP: áp thêm limit theo user id sau đăng nhập.
- IP dùng chung NAT: tránh ngưỡng quá thấp ở catalog để không chặn nhầm.
- Bot vượt CAPTCHA/limit: backend vẫn có per-user limit và idempotency bảo vệ.

## 6. Ràng buộc nghiệp vụ và kỹ thuật

- Rate limit phải chạy trước logic DB nặng.
- Endpoint mua vé có limit theo user id và route.
- Endpoint public catalog có limit rộng hơn và tận dụng cache.
- Không dùng rate limit thay thế kiểm tra nghiệp vụ.
- Cấu hình limit phải có thể thay đổi theo chiến dịch mở bán.

## 7. Tiêu chí chấp nhận

- Spam checkout bị chặn trước khi vào database.
- Request vượt ngưỡng nhận 429.
- Người dùng thật vẫn xem catalog được trong ngưỡng hợp lý.
- Database không bị request rác dội thẳng khi mở bán.
