# Technical Note
- Mục đích: ghi chép lại những kiến thức học được trong quá trình phát triển

### 1. PK phức hợp
- Cần định nghĩa class ID riêng implement từ `Serializable` và đánh anontation `@Embeddable` để mô tả PK phức hợp.

### 2. Idex
- Đánh các trường hay query + có độ phân biệt cao
- Thêm/Xóa index khi cần thiết.

### 3. Đánh Composite Index
- Dùng khi thường có câu query với điều kiện là nhiều cột.
- Cách thiết kế:
  1. EQUALITY columns first      (columns dùng = operator)
  2. SORT columns next           (columns trong ORDER BY)
  3. RANGE columns last          (columns dùng <, >, BETWEEN)

### 4. Pattern Builder
- Constructor không rõ nghĩa: `User user = new User("Thái", "thaidev@mail.com", 22, "Hà Nội", true, false);`
- Dùng Builder để build object tường minh -> `@Builder`
- `@Builder.Default` -> Đánh dấu giá trị default của trường khi khởi tạo.

### 5. Interface `JpaRepository`
- Cung cấp sẵn CRUD cơ bản: `findById`, `save`,...
- Không cần viết lệnh SQL.
- Extend `JpaRepository` -> Có inteface mới -> Spring Boot tự Implement findById, findByEmaill,... nếu ta khai báo.