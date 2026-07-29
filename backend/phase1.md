# 🔐 Lộ Trình Học Spring Security — Mức Cơ Bản

> **Mục tiêu:** Hiểu các khái niệm cốt lõi, cơ chế hoạt động của Spring Security, và có khả năng đọc hiểu / review mã nguồn do AI sinh ra một cách tự tin.

---

## 📌 Tổng Quan Roadmap

Spring Security là một framework bảo mật mạnh mẽ, nhưng có đường cong học tập khá dốc vì sự trừu tượng cao và nhiều lớp cấu hình. Lộ trình này được chia thành **4 Phase** theo hướng từ nền tảng đến thực chiến:

```
Phase 1 — Nền tảng bảo mật Web & Java
         ↓
Phase 2 — Kiến trúc & Cơ chế cốt lõi Spring Security
         ↓
Phase 3 — Xác thực & Phân quyền thực tế
         ↓
Phase 4 — Review mã nguồn AI & Nhận diện lỗi bảo mật
```

**Tổng thời gian ước tính:** 5–7 tuần (học 1–2 giờ/ngày)

---

## 🗺️ Danh Sách Các Phase & Sub-phase

| Phase | Sub-phase | Tên | Thời gian |
|-------|-----------|-----|-----------|
| 1 | 1.1 | HTTP & Giao tiếp Web | 2 ngày |
| 1 | 1.2 | Authentication vs Authorization | 1 ngày |
| 1 | 1.3 | Servlet & Filter trong Java | 2 ngày |
| 1 | 1.4 | Mật khẩu & Mã hóa cơ bản | 1 ngày |
| 2 | 2.1 | FilterChain — Kiến trúc tổng thể | 2 ngày |
| 2 | 2.2 | SecurityContext & Authentication Object | 2 ngày |
| 2 | 2.3 | Authentication Flow (Manager → Provider → UserDetails) | 2 ngày |
| 2 | 2.4 | Authorization Flow & HttpSecurity Config | 2 ngày |
| 3 | 3.1 | Form Login & Session-based Auth | 2 ngày |
| 3 | 3.2 | JWT — Tự implement từ đầu | 3 ngày |
| 3 | 3.3 | Method Security & Phân quyền nâng cao | 2 ngày |
| 3 | 3.4 | CSRF & CORS | 2 ngày |
| 3 | 3.5 | Oauth — Tự implement từ đầu | 3 ngày |
| 4 | 4.1 | Checklist Review Security Config | 2 ngày |
| 4 | 4.2 | Nhận diện lỗi trong code AI | 2 ngày |
| 4 | 4.3 | Debug & Thực hành Review thực tế | 2 ngày |

---

## Phase 1 — Nền Tảng Bảo Mật Web & Java

> **Thời gian:** ~1 tuần

---

### Phase 1.1 — HTTP & Giao Tiếp Web

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **HTTP là stateless** — mỗi request độc lập, server không nhớ ai đã gửi request trước. Tại sao điều này là vấn đề với bảo mật?
- **Request / Response lifecycle** — method (GET, POST, PUT...), headers, body, status code
- **Cookie** — server tạo, browser tự gửi kèm mọi request, có thể bị đánh cắp (HttpOnly, Secure flag)
- **Session** — server lưu trạng thái, browser chỉ giữ session ID qua cookie
- **Token (JWT, API Key)** — client tự lưu, tự gửi thủ công qua header `Authorization`
- **HTTPS / TLS** — mã hóa kênh truyền để chống nghe lén; không phải mã hóa dữ liệu trong database

#### ⭐ Trọng tâm

> **Cookie vs Token** — Hiểu rõ sự khác biệt này sẽ giải thích được hàng loạt quyết định thiết kế phía sau: tại sao CSRF tấn công được session nhưng không tấn công được JWT, tại sao CORS liên quan đến cookie,...

#### ✅ Đầu ra

- Vẽ được sơ đồ một HTTP request đi từ browser → server → response
- Giải thích được tại sao web cần thêm Cookie/Session/Token dù HTTP đã hoạt động được
- Phân biệt được 3 cách lưu trạng thái: Cookie, Session, Token — ưu/nhược điểm mỗi loại

---

### Phase 1.2 — Authentication vs Authorization

> **Thời gian:** ~1 ngày

#### Kiến thức cần nắm

- **Authentication (Xác thực)** — "Bạn là ai?" — xác minh danh tính bằng username/password, token, biometric...
- **Authorization (Phân quyền)** — "Bạn được làm gì?" — kiểm tra quyền truy cập sau khi đã xác thực
- Thứ tự bắt buộc: **Authentication phải xảy ra trước Authorization**
- Các hình thức Authentication phổ biến: Form Login, HTTP Basic, OAuth2, API Key, Certificate
- Các mô hình Authorization phổ biến: Role-based (RBAC), Permission-based, Attribute-based (ABAC)
- **Principal** — đại diện cho "người dùng đang đăng nhập" trong hệ thống

#### ⭐ Trọng tâm

> Đây là **khái niệm nền tảng nhất** của toàn bộ lộ trình. Spring Security thiết kế mọi thứ xoay quanh hai khái niệm này. Nhầm lẫn giữa hai khái niệm này sẽ dẫn đến thiết kế hệ thống sai.
>
> Ví dụ thực tế: Một user đã đăng nhập (Authentication ✅) nhưng cố truy cập trang admin (Authorization ❌) → HTTP 403 Forbidden, không phải 401 Unauthorized.

#### ✅ Đầu ra

- Giải thích được sự khác nhau giữa 401 Unauthorized và 403 Forbidden
- Đặt được câu hỏi đúng khi thiết kế: "Đây là vấn đề xác thực hay phân quyền?"

---

### Phase 1.3 — Servlet & Filter trong Java

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **Servlet** — đơn vị xử lý HTTP request trong Java EE; `HttpServlet`, `doGet()`, `doPost()`
- **Servlet Container** — Tomcat, Jetty... là môi trường chạy Servlet, nhận request từ network và dispatch
- **Servlet Filter** — interceptor chạy *trước và sau* Servlet; có thể đọc/sửa request, block request, hoặc chuyển tiếp
- **Filter Chain** — nhiều Filter xếp thành chuỗi, thứ tự quan trọng; mỗi Filter gọi `chain.doFilter()` để tiếp tục
- **`HttpServletRequest` / `HttpServletResponse`** — object đại diện cho request/response trong Servlet API
- **DispatcherServlet** — "Front Controller" của Spring MVC, là một Servlet đặc biệt nhận tất cả request rồi dispatch đến đúng Controller

```
Request → Servlet Container → Filter 1 → Filter 2 → Filter N → DispatcherServlet → Controller
                           ←           ←           ←          ←                  ←
                                           (Response)
```

#### ⭐ Trọng tâm

> **Filter là nền móng của Spring Security.** Toàn bộ Spring Security là một chuỗi Filter được inject vào Servlet container. Không hiểu Filter hoạt động thế nào (đặc biệt là `chain.doFilter()`), sẽ không hiểu tại sao Spring Security có thể block request *trước khi vào Controller*.

#### ✅ Đầu ra

- Viết được một Servlet Filter đơn giản tự tay (log request, check header...)
- Giải thích được: nếu một Filter không gọi `chain.doFilter()`, điều gì xảy ra?
- Vẽ được luồng request qua Filter Chain đến DispatcherServlet

---

### Phase 1.4 — Mật Khẩu & Mã Hóa Cơ Bản

> **Thời gian:** ~1 ngày

#### Kiến thức cần nắm

- **Tại sao không lưu plain text?** — Database bị leak, mọi tài khoản bị lộ ngay lập tức
- **Encryption (Mã hóa)** — có thể giải mã ngược; dùng cho dữ liệu cần đọc lại (AES, RSA)
- **Hashing (Băm)** — một chiều, không giải mã ngược; dùng cho mật khẩu (MD5, SHA-256 — nhưng đã yếu)
- **Salt** — chuỗi ngẫu nhiên thêm vào trước khi hash; chống rainbow table attack
- **BCrypt** — thuật toán hash hiện đại, tích hợp salt, có cost factor để làm chậm brute-force
- **`PasswordEncoder` interface** trong Spring Security — `BCryptPasswordEncoder` là implementation mặc định và được khuyến nghị
- `encode(rawPassword)` vs `matches(rawPassword, encodedPassword)` — hai method cốt lõi

#### ⭐ Trọng tâm

> Hiểu **tại sao BCrypt chứ không phải SHA-256** — BCrypt chậm có chủ đích (cost factor), khiến brute-force tốn kém hơn rất nhiều. Đây là lý do Spring Security dùng `BCryptPasswordEncoder` làm default.

#### ✅ Đầu ra

- Giải thích được quy trình lưu mật khẩu: nhận plain text → hash → lưu DB
- Giải thích được quy trình verify: nhận plain text → hash với cùng salt → so sánh
- Biết tại sao không thể "giải mã" mật khẩu đã lưu trong DB