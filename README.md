# TicketBox

## Tech stack

- **Backend:** Java 21, Spring Boot 4.0.6, Maven, Spring Data JPA, Spring Security (JWT/OAuth2), Flyway.
- **Frontend:** React 19, TypeScript 5, Vite 6, Tailwind CSS 4, React Router 7.
- **Hạ tầng:** PostgreSQL 15, Redis 7, RabbitMQ 3.12, MinIO, Nginx, Docker Compose.
- **Tích hợp:** Google OAuth2, SMTP, VNPay, MoMo, Gemini.

## Cấu hình môi trường

Yêu cầu: JDK 21, Node.js 22 LTS + npm, Docker + Docker Compose. Từ thư mục gốc, tạo file cấu hình nếu chưa có:

```bash
cp -n backend/.env.example backend/.env
```

Chỉnh `backend/.env`:

| Nhóm biến | Cấu hình local |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/ticketbox`, `postgres`, `password` |
| `REDIS_*`, `RABBITMQ_*`, `MINIO_*` | Giữ giá trị trong `.env.example` để kết nối Docker |
| `PORT` | `8082` — khớp upstream của Nginx |
| `JWT_ACCESS_TOKEN`, `JWT_REFRESH_TOKEN`, `TICKET_QR_SECRET` | Điền secret riêng cho từng biến; có thể tạo bằng `openssl rand -hex 32` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Điền thông tin OAuth2; nếu chưa dùng, xóa hai dòng trống để dùng placeholder mặc định |
| `MAIL_*`, `VNPAY_*`, `MOMO_*`, `GEMINI_API_KEY` | Điền thông tin dịch vụ tương ứng khi sử dụng |
| `NGROK_AUTHTOKEN` | Cần khi chạy ngrok |

Frontend mặc định dùng Vite proxy, không cần `.env`. Nếu dùng API riêng, tạo `frontend/.env.local` với `VITE_API_BASE_URL=<URL API>` rồi khởi động lại Vite. Cấu hình đầy đủ nằm trong `backend/src/main/resources/application.yml`.

## Chạy local

### 1. Docker

Chạy các dịch vụ hỗ trợ từ thư mục gốc:

```bash
docker compose up -d
```

Dừng các container bằng .
```bash
docker compose down
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

Mặc định dùng profile `api`, tự đọc `.env` và chạy migration bằng Flyway khi khởi động.

### 3. Frontend

Mở terminal khác, từ thư mục gốc:

```bash
cd frontend
npm install
npm run build
npm run dev
```

Truy cập [localhost:3001](http://localhost:3001). Build frontend bằng `npm run build`.

## Nginx và Backend

```text
Frontend (3001) → Vite proxy → Nginx (8080) → Backend (127.0.0.1:8082)
Ngrok                       → Nginx (8080) → Backend (127.0.0.1:8082)
```

Nginx lắng nghe ở `8080`, áp dụng giới hạn tần suất request rồi chuyển tiếp tới Backend ở `8082` qua `proxy_pass` trong `nginx/nginx.conf`. Backend mặc định chỉ bind `127.0.0.1`; Nginx dùng host networking để kết nối tới Backend trên máy host.

- Đổi `PORT` của Backend: sửa cả địa chỉ `proxy_pass` của Nginx.
- Đổi cổng `listen` của Nginx: sửa các `target` trong `frontend/vite.config.ts` và cổng đích ngrok trong `docker-compose.yml`.
- Khởi động lại dịch vụ tương ứng sau khi đổi cấu hình. Gọi trực tiếp `8082` sẽ bỏ qua giới hạn request của Nginx.

## Các cổng thường dùng

| Dịch vụ | Cổng local | Ghi chú |
| --- | --- | --- |
| Frontend | `3001` | Giao diện web |
| Backend API | `8082` | Bind mặc định `127.0.0.1` |
| Nginx | `8080` | Gateway cho API |
| PostgreSQL | `5432` | DB `ticketbox`, tài khoản `postgres` / `password` |
| Adminer | `8081` | Quản trị DB; server: `postgres` |
| Redis | `6379` | Không mật khẩu ở local |
| RedisInsight | `5540` | Quản trị Redis; kết nối `redis:6379` |
| RabbitMQ | `5672` / `15672` | AMQP / web quản trị (`guest` / `guest`) |
| MinIO | `9000` / `9001` | API / console (`admin` / `admin123456`) |
