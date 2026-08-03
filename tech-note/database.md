### 1. Thư viện hỗ trợ Migration
- `flywaydb`
- Khác `prisma` và `knextjs` viết schema bằng mã nguồn `JavaScript`, `flywaydb` viết schema bằng `.sql`
- Tính năng:
    - Quản lý phiên bản schema tự động:
        - Nếu đã chạy `V1`, `V2` thì lần sau chỉ chạy `V3`
        - Chạy đúng thứ tự phiên bản
    - Kiểm tra ai đã sửa
    - Chạy tự động khi deploy
- Khi khởi động Spring Boot App sẽ tự động chạy migration

#### 2. Spring Data Redis
- Dependency hỗ trợ Redis
- RedisTemplate: abstract layer của Spring Data Redis -> cung cấp các phương thức để thực hiện các thao tác đọc/ghi dữ liệu vào Redis
- StringRedisTemplate: cũng là RedisTemplate nhưng Key và Value đều là String