# Technical Note
- Mục đích: ghi chép lại những kiến thức học được trong quá trình phát triển

### JPA

#### 1. PK phức hợp
- Cần định nghĩa class ID riêng implement từ `Serializable` và đánh anontation `@Embeddable` để mô tả PK phức hợp.

#### 2. Index
- Đánh các trường hay query + có độ phân biệt cao
- Thêm/Xóa index khi cần thiết.

#### 3. Đánh Composite Index
- Dùng khi thường có câu query với điều kiện là nhiều cột.
- Cách thiết kế:
  1. EQUALITY columns first      (columns dùng = operator)
  2. SORT columns next           (columns trong ORDER BY)
  3. RANGE columns last          (columns dùng <, >, BETWEEN)

### Spring Security

#### 1. Cách ký JWT
- Dùng 1 JWT Secret -> Sign và Verify cùng 1 Key -> Dùng cho Monolith khi tự ký tự Verify
- Dùng cặp RS256 -> Sign bằng Private Key và Verify bằng Public Key -> Dùng cho Microservice khi Service Sign khác Service Verify.

#### 2. Claims nên đặt trong JWT
- Refresh token: đặt UUID
- Access token: đặt UUID + ROLE

#### 3. Cấu trúc thư mục

#### 4. UserDetails và UserDetailsService
- Được Spring Security dùng để định danh User sau khi đã xác thực.
- Có thể implement từ UserDetails và UserDetailsService để tuy chỉnh cho phù hợp.

#### 5. Spring Data Redis
- Dependency hỗ trợ Redis
- RedisTemplate: abstract layer của Spring Data Redis -> cung cấp các phương thức để thực hiện các thao tác đọc/ghi dữ liệu vào Redis
- StringRedisTemplate: cũng là RedisTemplate nhưng Key và Value đều là String