#### 1. Cách ký JWT
- Dùng 1 JWT Secret -> Sign và Verify cùng 1 Key -> Dùng cho Monolith khi tự ký tự Verify
- Dùng cặp RS256 -> Sign bằng Private Key và Verify bằng Public Key -> Dùng cho Microservice khi Service Sign khác Service Verify.

#### 2. Claims nên đặt trong JWT
- Refresh token: đặt UUID
- Access token: đặt UUID + ROLE

#### 3. UserDetails và UserDetailsService
- Được Spring Security dùng để định danh User sau khi đã xác thực.
- Có thể implement từ UserDetails và UserDetailsService để tuy chỉnh cho phù hợp.

#### 4. Lưu Token
- Refresh Token: 
    - Lưu ở Cookie storage của browser
    - Tự động đính kèm khi gửi api đến `**/refresh`
    - Bật HttpOnly, Secure và cấu hình Samesite nếu frontend và backend khác origin
    - Gửi qua Header Cookie
- Access Token
    - Lưu ở in-memory: F5 là mất
    - Lưu ở Local storage: F5 không mất nhưng nguy cơ XSS
    - Gửi qua Header Authorization: Bearer
- Không cần bật csrf