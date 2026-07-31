### 1. Thư viện hỗ trợ Migration
- `flywaydb`
- Khác `prisma` và `knextjs` viết schema bằng mã nguồn `JavaScript`, `flywaydb` viết schema bằng `.sql`
- Tính năng:
    - Quản lý phiên bản schema tự động:
        - Nếu đã chạy `V1`, `V2` thì lần sau chỉ chạy `V3`
        - Chạy đúng thứ tự phiên bản
    - Kiểm tra ai đã sửa
    - Chạy tự động khi Deploy
