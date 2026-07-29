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

## Phase 3 — Xác Thực & Phân Quyền Thực Tế

> **Thời gian:** ~1.5 tuần

---

### Phase 3.1 — Form Login & Session-based Auth

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **Luồng đầy đủ Form Login:**
  1. User POST `/login` với username/password
  2. `UsernamePasswordAuthenticationFilter` bắt request
  3. Tạo `UsernamePasswordAuthenticationToken` (chưa xác thực)
  4. Gọi `AuthenticationManager.authenticate()`
  5. `DaoAuthenticationProvider` gọi `UserDetailsService.loadUserByUsername()`
  6. Verify password bằng `PasswordEncoder.matches()`
  7. Tạo `Authentication` đã xác thực → lưu vào `SecurityContextHolder`
  8. Lưu `SecurityContext` vào **HTTP Session**
  9. Redirect đến success URL
- **`successHandler` / `failureHandler`** — customize hành vi sau login thành công/thất bại (đặc biệt hữu ích cho REST API: trả JSON thay vì redirect)
- **`HttpSessionSecurityContextRepository`** — component lưu/load `SecurityContext` vào/từ session
- **Session fixation protection** — Spring Security tự tạo session ID mới sau login để chống tấn công session fixation
- **`HttpSession` lifecycle** — tạo khi login, hủy khi logout hoặc timeout

#### ⭐ Trọng tâm

> **Form Login không phù hợp cho REST API trả JSON.** Mặc định Spring Security redirect đến `/login` khi chưa xác thực (HTTP 302) — điều này phá vỡ REST client. Cần custom `AuthenticationEntryPoint` để trả `401 JSON` thay vì redirect.

#### ✅ Đầu ra

- Trace được toàn bộ luồng Form Login từ POST request đến session được tạo
- Custom được `successHandler` để trả JSON response thay vì redirect
- Giải thích được session fixation là gì và Spring Security bảo vệ thế nào

---

### Phase 3.2 — JWT: Tự Implement Từ Đầu

> **Thời gian:** ~3 ngày

#### Kiến thức cần nắm

- **Cấu trúc JWT:** `Header.Payload.Signature`
  - Header: algorithm (`HS256`, `RS256`)
  - Payload: claims — `sub` (subject), `iat` (issued at), `exp` (expiration), custom claims
  - Signature: HMAC hoặc RSA signature để verify token không bị giả mạo
- **JWT không được mã hóa** (chỉ Base64 encoded) — payload đọc được bởi bất kỳ ai có token; **không lưu thông tin nhạy cảm trong payload**
- **Luồng JWT:**
  1. Login → server verify credentials → tạo JWT → trả về client
  2. Client lưu token (memory hoặc cookie HttpOnly)
  3. Mỗi request: client gửi `Authorization: Bearer <token>` trong header
  4. Server validate token (signature + expiry) → extract claims → set Authentication
- **Spring Security KHÔNG có built-in JWT filter** — phải tự viết `OncePerRequestFilter`:

```java
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, 
                                    HttpServletResponse res,
                                    FilterChain chain) {
        String token = extractToken(req);           // lấy token từ header
        if (token != null && jwtService.isValid(token)) {
            UserDetails user = userDetailsService
                .loadUserByUsername(jwtService.extractUsername(token));
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth); // set thủ công
        }
        chain.doFilter(req, res);                   // PHẢI gọi để tiếp tục chain
    }
}
```

- **Refresh Token** — JWT access token nên có expiry ngắn (15–60 phút); refresh token tồn tại lâu hơn, dùng để lấy access token mới mà không cần login lại
- **Lưu JWT ở đâu:** cookie HttpOnly (an toàn hơn, chống XSS) vs localStorage (dễ implement, dễ bị XSS)
- **Thư viện:** `io.jsonwebtoken:jjwt` (JJWT) hoặc `com.auth0:java-jwt`

#### ⭐ Trọng tâm

> **`SecurityContextHolder.getContext().setAuthentication(auth)` — dòng quan trọng nhất.** Đây là bước "thủ công" mà nhiều người quên: sau khi validate JWT, phải *tự set* Authentication vào SecurityContext. Nếu không set, Spring Security vẫn coi request là anonymous dù token hợp lệ.

#### ✅ Đầu ra

- Implement được JwtService (generate + validate token) và JwtAuthFilter từ đầu
- Cấu hình được `SecurityFilterChain` để dùng JWT filter thay vì form login
- Giải thích được tại sao cần set `STATELESS` session management khi dùng JWT
- Biết sự khác nhau giữa access token và refresh token

---

### Phase 3.3 — Method Security & Phân Quyền Nâng Cao

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **Bật Method Security:** cần thêm `@EnableMethodSecurity` vào `@Configuration` class (lỗi hay gặp: quên annotation này)
- **`@PreAuthorize`** — kiểm tra quyền *trước* khi method thực thi:
  ```java
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteUser(Long id) { ... }

  @PreAuthorize("hasAuthority('order:read')")
  public List<Order> getOrders() { ... }

  @PreAuthorize("#username == authentication.name") // chỉ xem account của chính mình
  public User getProfile(String username) { ... }
  ```
- **`@PostAuthorize`** — kiểm tra quyền *sau* khi method thực thi (dùng khi cần kiểm tra kết quả trả về):
  ```java
  @PostAuthorize("returnObject.owner == authentication.name")
  public Document getDocument(Long id) { ... }
  ```
- **`@Secured`** — đơn giản hơn, không dùng SpEL: `@Secured("ROLE_ADMIN")`
- **SpEL expressions phổ biến:** `hasRole()`, `hasAuthority()`, `hasAnyRole()`, `isAuthenticated()`, `isAnonymous()`, `#paramName` (tham số method)
- **URL-based vs Method-based:**
  - URL-based (`requestMatchers`): bảo vệ ở tầng HTTP, coarse-grained
  - Method-based (`@PreAuthorize`): bảo vệ ở tầng business logic, fine-grained; phù hợp khi cùng endpoint nhưng xử lý khác nhau theo quyền

#### ⭐ Trọng tâm

> **`@EnableMethodSecurity` hay bị quên.** AI thường generate code dùng `@PreAuthorize` nhưng quên thêm `@EnableMethodSecurity` vào config — annotation sẽ bị ignored hoàn toàn mà không có lỗi nào báo. Đây là một trong những lỗi âm thầm nguy hiểm nhất.

#### ✅ Đầu ra

- Dùng được `@PreAuthorize` với cả role check và custom SpEL expression
- Giải thích được khi nào nên dùng URL-based authorization, khi nào nên dùng method-based
- Nhận ra khi `@PreAuthorize` không hoạt động và biết cách debug (check `@EnableMethodSecurity`)

---

### Phase 3.4 — CSRF & CORS

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

**CSRF (Cross-Site Request Forgery):**
- **Cơ chế tấn công:** trang web độc hại lừa browser của user gửi request đến server khác kèm cookie session → server không phân biệt được request hợp lệ hay không
- **Tại sao JWT chống được CSRF:** JWT được gửi qua header `Authorization`, không tự động đính kèm như cookie → trang độc hại không thể giả mạo
- **CSRF Token:** server sinh token ngẫu nhiên, nhúng vào form HTML → khi submit, gửi kèm token → server verify. Trang độc hại không thể đọc token này (same-origin policy)
- **`CsrfTokenRepository`** — nơi lưu CSRF token (mặc định: session; có thể dùng cookie)
- **Khi nào disable CSRF:** chỉ khi app **hoàn toàn stateless** và **không dùng cookie để auth**

**CORS (Cross-Origin Resource Sharing):**
- **Same-Origin Policy:** browser chặn request từ `origin-a.com` đến `origin-b.com` để bảo vệ user
- **CORS** cho phép server khai báo những origin nào được phép gọi đến
- **CORS headers:** `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`
- **Preflight request:** browser gửi `OPTIONS` request trước khi gửi request thực để hỏi server có cho phép không
- **Lỗi cấu hình nghiêm trọng:** `allowedOrigins("*")` + `allowCredentials(true)` — browser sẽ từ chối vì spec không cho phép wildcard kết hợp credentials
- **Cấu hình CORS đúng cách:**
  ```java
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(List.of("https://myfrontend.com")); // cụ thể, không wildcard
      config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
      config.setAllowCredentials(true);
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", config);
      return source;
  }
  ```
- **Lỗi phổ biến:** cấu hình CORS ở `@CrossOrigin` hoặc `WebMvcConfigurer` nhưng Spring Security filter chạy *trước* Spring MVC → request bị block trước khi đến được CORS config của MVC

#### ⭐ Trọng tâm

> **CORS phải được cấu hình ở tầng Spring Security**, không chỉ ở Spring MVC. Vì Security Filter chạy trước DispatcherServlet, một CORS preflight request (`OPTIONS`) không có token sẽ bị Spring Security chặn trước khi đến được `@CrossOrigin`. Giải pháp: dùng `http.cors()` với `CorsConfigurationSource` bean.

#### ✅ Đầu ra

- Giải thích được tại sao CSRF nguy hiểm với session-based auth nhưng không ảnh hưởng JWT
- Cấu hình được CORS đúng cách trong Spring Security (không chỉ Spring MVC)
- Nhận biết lỗi `allowedOrigins("*")` + `allowCredentials(true)` và giải thích vấn đề

# Phase 3.5 — OAuth2 & OpenID Connect

> **Thời gian:** ~2 ngày

---

## Kiến thức cần nắm

### Các khái niệm nền tảng

- **OAuth2** — giao thức *phân quyền* (Authorization): cho phép app A truy cập tài nguyên của user ở app B mà không cần biết mật khẩu của user
- **OpenID Connect (OIDC)** — lớp *xác thực* (Authentication) xây trên OAuth2; bổ sung **ID Token** để biết "user này là ai"
- Phân biệt rõ: OAuth2 trả lời "app được làm gì?", OIDC trả lời "user là ai?"
- **Các role trong OAuth2:**
  - **Resource Owner** — user sở hữu dữ liệu
  - **Client** — ứng dụng muốn truy cập dữ liệu (app Spring Boot của bạn)
  - **Authorization Server** — nơi xác thực user và cấp token (Google, GitHub, Keycloak...)
  - **Resource Server** — nơi chứa dữ liệu được bảo vệ (có thể là chính app của bạn)
- **Token types:**
  - **Access Token** — dùng để truy cập Resource Server; thường là JWT
  - **Refresh Token** — dùng để lấy Access Token mới khi hết hạn
  - **ID Token** — chỉ có trong OIDC; chứa thông tin về user (name, email...)
  - **Authorization Code** — mã tạm thời, đổi lấy token; chỉ dùng một lần

---

### Authorization Code Flow — luồng phổ biến và an toàn nhất

```
User click "Login with Google"
     ↓
Client redirect user → Authorization Server (Google)
     ↓ (user đăng nhập, đồng ý cấp quyền)
Authorization Server redirect về Client kèm Authorization Code
     ↓
Client dùng Code + Client Secret gọi thẳng đến Authorization Server
     ↓
Authorization Server trả Access Token + ID Token (+ Refresh Token)
     ↓
Client dùng Access Token gọi Resource Server
```

> **Tại sao dùng Code thay vì trả thẳng Token qua redirect?**
> URL redirect có thể bị lộ qua browser history, server log. Code ngắn, dùng một lần, và việc đổi lấy token xảy ra server-to-server — an toàn hơn nhiều.

---

### Spring Security OAuth2 Client — `oauth2Login()`

Dùng khi app của bạn là **Client**, muốn cho user đăng nhập bằng Google/GitHub/...

Cấu hình trong `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: YOUR_CLIENT_ID
            client-secret: YOUR_CLIENT_SECRET
            scope: openid, profile, email
```

- Spring Security tự xử lý toàn bộ Authorization Code Flow
- Sau khi login thành công, `Authentication` là `OAuth2AuthenticationToken`, principal là `OAuth2User`
- Customize xử lý sau login thành công: implement `OAuth2UserService` để map OAuth2 user sang domain user của app

---

### Spring Security OAuth2 Resource Server — `oauth2ResourceServer()`

Dùng khi app của bạn là **Resource Server**, cần validate Bearer token từ request:

```java
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwkSetUri("https://accounts.google.com/.well-known/openid-configuration"))
);
```

- Spring Security tự fetch public key từ Authorization Server để verify JWT signature
- Khác với JWT tự implement (Phase 3.2): không cần tự quản lý secret key — dùng public key infrastructure của Authorization Server

---

### Scopes & Authorities

- **Scope** — quyền mà client yêu cầu: `openid`, `profile`, `email`, `read:users`...
- Spring Security map scope thành `GrantedAuthority` với prefix `SCOPE_`: scope `read` → authority `SCOPE_read`
- Phân biệt scope (OAuth2, do Authorization Server cấp) vs role (app nội bộ, do app tự quản lý)

---

## ⭐ Trọng tâm

> **Hai use case khác nhau hoàn toàn, không được nhầm lẫn:**
>
> **1. `oauth2Login()`** — App của bạn là **Client**, dùng Google/GitHub để xác thực user.
> User đăng nhập bằng tài khoản Google, app nhận thông tin user từ Google.
> Thường dùng cho web app có giao diện.
>
> **2. `oauth2ResourceServer()`** — App của bạn là **Resource Server**, validate token do Authorization Server cấp.
> Client (mobile app, SPA...) gọi API của bạn kèm token.
> Thường dùng cho REST API.
>
> AI hay generate nhầm giữa hai loại này — đây là điểm cần kiểm tra kỹ khi review.

---

## ✅ Đầu ra

Sau Phase này, bạn có thể:

- Giải thích được Authorization Code Flow bằng lời, không cần nhớ từng HTTP call
- Phân biệt được khi nào dùng `oauth2Login()` vs `oauth2ResourceServer()`
- Đọc được một `SecurityConfig` có OAuth2 và hiểu app đang đóng vai trò gì (Client hay Resource Server)
- Nhận biết được lỗi khi AI nhầm lẫn hai use case này trong code