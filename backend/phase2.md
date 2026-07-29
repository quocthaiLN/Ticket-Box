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

## Phase 2 — Kiến Trúc & Cơ Chế Cốt Lõi Spring Security

> **Thời gian:** ~1.5 tuần

---

### Phase 2.1 — FilterChain: Kiến Trúc Tổng Thể

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **`DelegatingFilterProxy`** — cầu nối giữa Servlet container và Spring ApplicationContext; cho phép Spring quản lý Filter lifecycle
- **`FilterChainProxy`** — bean trong Spring context, là entry point thực sự của Spring Security; nhận request từ `DelegatingFilterProxy`
- **`SecurityFilterChain`** — một tập hợp Filter áp dụng cho một tập URL pattern; một app có thể có nhiều `SecurityFilterChain`
- **Thứ tự các Filter tiêu biểu** (từ trên xuống dưới):
  1. `SecurityContextPersistenceFilter` — load/save SecurityContext từ session
  2. `UsernamePasswordAuthenticationFilter` — xử lý form login
  3. `BasicAuthenticationFilter` — xử lý HTTP Basic
  4. `BearerTokenAuthenticationFilter` — xử lý JWT/OAuth2 token (nếu có)
  5. `ExceptionTranslationFilter` — bắt exception, redirect đến login hoặc trả 403
  6. `AuthorizationFilter` — kiểm tra quyền truy cập (thay thế `FilterSecurityInterceptor`)

```
HTTP Request
     ↓
DelegatingFilterProxy (Servlet layer)
     ↓
FilterChainProxy (Spring layer)
     ↓
SecurityFilterChain khớp URL
     ↓ (Filter 1 → Filter 2 → ... → Filter N)
DispatcherServlet → Controller
```

#### ⭐ Trọng tâm

> **Thứ tự Filter là bất biến và có ý nghĩa.** `ExceptionTranslationFilter` phải đứng sau các Authentication Filter để bắt được exception từ chúng. Nhiều lỗi cấu hình Spring Security xuất phát từ việc custom filter được đặt sai vị trí trong chain.

#### ✅ Đầu ra

- Vẽ được sơ đồ kiến trúc tổng thể từ HTTP request đến Filter chain
- Giải thích được vai trò của `DelegatingFilterProxy` và `FilterChainProxy`
- Đọc log debug và nhận ra thứ tự các filter đang được apply

---

### Phase 2.2 — SecurityContext & Authentication Object

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **`SecurityContext`** — container lưu thông tin bảo mật của request hiện tại
- **`SecurityContextHolder`** — nơi lưu trữ `SecurityContext`; dùng **ThreadLocal** mặc định (mỗi thread có context riêng)
- **`Authentication` interface** — object trung tâm, gồm 3 thành phần:
  - `principal` — danh tính (thường là `UserDetails` object sau khi xác thực)
  - `credentials` — thông tin xác thực (password, token; thường bị xóa sau auth thành công)
  - `authorities` — danh sách quyền (`GrantedAuthority`)
- **`isAuthenticated()`** — `false` trước khi xác thực, `true` sau khi xác thực thành công
- **Lấy user hiện tại trong Controller:**

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();

// Hoặc dùng annotation:
public String profile(@AuthenticationPrincipal UserDetails user) { ... }
```

- **Vấn đề với `@Async`** — ThreadLocal không được kế thừa sang thread mới → `SecurityContext` bị null trong async method → cần cấu hình `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)`

#### ⭐ Trọng tâm

> **ThreadLocal strategy** — Hiểu tại sao `SecurityContextHolder` dùng ThreadLocal (mỗi request chạy trên 1 thread riêng) và tại sao điều này gây vấn đề với `@Async`, reactive programming (WebFlux), hay virtual threads (Java 21). Đây là nguồn gốc của nhiều lỗi khó debug trong production.

#### ✅ Đầu ra

- Gọi được `SecurityContextHolder` để lấy thông tin user hiện tại trong bất kỳ tầng nào (Service, Controller)
- Giải thích được `Authentication` object gồm những gì và được set ở đâu trong luồng xử lý
- Nhận biết được tình huống `SecurityContext` bị null và giải thích nguyên nhân

---

### Phase 2.3 — Authentication Flow

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **`AuthenticationManager`** — interface với một method `authenticate(Authentication)`; là entry point của quá trình xác thực
- **`ProviderManager`** — implementation phổ biến nhất của `AuthenticationManager`; chứa danh sách `AuthenticationProvider` và lần lượt thử từng cái
- **`AuthenticationProvider`** — interface xử lý một loại xác thực cụ thể:
  - `DaoAuthenticationProvider` — xác thực bằng username/password qua `UserDetailsService`
  - `JwtAuthenticationProvider` — xác thực bằng JWT token
  - Có thể tự implement để xác thực theo logic riêng (OTP, certificate...)
- **`UserDetailsService`** — interface với một method `loadUserByUsername(String username)`; là cầu nối đến database
- **`UserDetails`** — interface mô tả user (username, password, authorities, account status)
- **`PasswordEncoder`** — được `DaoAuthenticationProvider` dùng để verify password

```
Filter nhận request
     ↓
Tạo Authentication object (chưa xác thực)
     ↓
AuthenticationManager.authenticate()
     ↓
ProviderManager thử từng AuthenticationProvider
     ↓
DaoAuthenticationProvider.authenticate()
     ↓
UserDetailsService.loadUserByUsername()  ←── query database
     ↓
PasswordEncoder.matches()
     ↓
Authentication object (đã xác thực) → lưu vào SecurityContextHolder
```

#### ⭐ Trọng tâm

> **`UserDetailsService` là điểm tùy biến chính.** Hầu hết mọi project đều cần implement interface này để load user từ database của mình. Hiểu rõ nó nhận gì, trả về gì, và được gọi ở đâu trong flow là kỹ năng thiết yếu.

#### ✅ Đầu ra

- Tự implement `UserDetailsService` load user từ database
- Trace được toàn bộ luồng từ khi nhận username/password đến khi `Authentication` được lưu vào `SecurityContextHolder`
- Giải thích được khi nào nên implement `UserDetailsService` vs khi nào cần custom `AuthenticationProvider`

---

### Phase 2.4 — Authorization Flow & HttpSecurity Config

> **Thời gian:** ~2 ngày

#### Kiến thức cần nắm

- **`GrantedAuthority`** — interface đại diện cho một quyền; `SimpleGrantedAuthority` là implementation đơn giản nhất
- **Role vs Authority:**
  - Role: `ROLE_ADMIN`, `ROLE_USER` — nhóm quyền (Spring tự thêm prefix `ROLE_`)
  - Authority: `user:read`, `order:delete` — quyền chi tiết, granular hơn
  - `hasRole("ADMIN")` tương đương `hasAuthority("ROLE_ADMIN")`
- **`AuthorizationManager`** (Spring Security 5.5+) — thay thế `AccessDecisionManager`, đơn giản và linh hoạt hơn
- **Cấu hình `HttpSecurity` — các block quan trọng:**

```java
http
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/public/**").permitAll()          // ai cũng vào được
      .requestMatchers("/admin/**").hasRole("ADMIN")      // chỉ ADMIN
      .requestMatchers("/api/**").hasAuthority("api:read")// chỉ có quyền api:read
      .anyRequest().authenticated()                       // còn lại cần login
  )
  .formLogin(form -> form
      .loginPage("/login").permitAll()
  )
  .csrf(csrf -> csrf.disable())                           // chỉ disable khi stateless
  .sessionManagement(session -> session
      .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // cho JWT
  );
```

- **Thứ tự rule trong `authorizeHttpRequests` rất quan trọng** — rule đầu tiên khớp sẽ được áp dụng, các rule sau bị bỏ qua

#### ⭐ Trọng tâm

> **Thứ tự `requestMatchers`** — Đây là lỗi cực phổ biến: đặt `.anyRequest().authenticated()` *trước* các rule cụ thể khiến tất cả rule phía sau không bao giờ được kiểm tra. Rule cụ thể (specific) phải luôn đứng trước rule chung (general).

#### ✅ Đầu ra

- Đọc một `SecurityFilterChain` bean và giải thích từng rule authorization
- Viết được cấu hình phân quyền cho một REST API cơ bản có nhiều role
- Giải thích được tại sao thứ tự `requestMatchers` quan trọng bằng ví dụ cụ thể

---

## 📚 Tài Nguyên Học Tập Gợi Ý

**Tài liệu chính thức (ưu tiên):**
- [Spring Security Reference Docs](https://docs.spring.io/spring-security/reference/) — nguồn đáng tin cậy nhất, đọc phần Architecture trước
- [Spring Security Architecture Guide](https://spring.io/guides/topicals/spring-security-architecture) — bài viết ngắn nhưng rất súc tích về kiến trúc

**Thực hành:**
- Tự viết project nhỏ theo thứ tự: Form Login → JWT → Method Security
- Clone một project trên GitHub, đọc `SecurityConfig` trước khi đọc phần còn lại
- Bật `spring.security.debug=true` và quan sát log khi gọi các endpoint

**Khi dùng AI để học:**
- Hỏi AI giải thích *từng dòng* config — đừng copy cả block
- Luôn hỏi thêm: "Đoạn code này có lỗ hổng bảo mật nào không?"
- Cross-check với Spring Security docs chính thức khi AI giải thích cơ chế

---

## 🎯 Tóm Tắt Hành Trình

```
[1.1] HTTP & Cookie/Session/Token
[1.2] Authentication vs Authorization       →  Nền tảng tư duy
[1.3] Servlet Filter                        ↓
[1.4] Password Hashing
                                         [2.1] FilterChain Architecture
                                         [2.2] SecurityContext & Auth Object  →  Hiểu cơ chế
                                         [2.3] Authentication Flow            ↓
                                         [2.4] Authorization & HttpSecurity
[3.1] Form Login + Session
[3.2] JWT từ đầu                         →  Biết làm thực tế
[3.3] Method Security (@PreAuthorize)    ↓
[3.4] CSRF & CORS
                                         [4.1] Checklist Review Config
                                         [4.2] Nhận diện lỗi AI              →  Tự tin review
                                         [4.3] Debug thực tế                 ↓
```

> **Nguyên tắc vàng:** Spring Security không khó nếu bạn học theo hướng "tại sao nó được thiết kế như vậy?" thay vì "copy config này vào là xong". Mỗi khi gặp một class hay annotation mới, hãy hỏi: *Nó nằm ở đâu trong filter chain? Nó được gọi khi nào?*