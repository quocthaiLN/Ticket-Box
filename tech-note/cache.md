#### 1. Thiết kế key cho Redis cache
- `GET /concerts`
    - Do có phân trang + cấu hình key = `concerts:all`
        - PAGE 0 gồm offset 1, 2, 3 lần đầu -> cache miss -> được đưa vào Redis -> PAGE 1 gồm offset 4, 5, 6-> cache hit dù trong cache chỉ có PAGE 0 gồm offset 1, 2, 3
    - Cần cache cho tất cả các PAGE + FILTER + Hash để giảm độ dài -> dùng `concerts:all:{hash}` để cache các biến dạng của URL
- `GET /concerts/{id}` = `concerts:{id}`
- `GET /concerts/{id}/metadata` = `concerts:{id}:metadata`

#### 2. Cấu hình Single Flight
- 100 request cache miss -> chỉ cho 1 request đầu truy vấn database -> các request sau đợi
- 2 cách triển khai
    - In-Memory -> Dùng `HashMap<URL, isProcessing>` -> mỗi instace có một HashMap riêng -> 5 instance thì có 5 request truy vấn database nếu cache miss + không tốn RTT
    - Distributed Lock -> Dùng Redis -> dù có bao nhiêu instance thì cũng chỉ có 1 request truy vấn database nếu cache miss + tốn RTT

#### 3. Warm-up trước giờ mở bán


#### 4. `Class<T>` và `TypeReference<T>`
- `Class<T>` -> dùng cho class dạng thường `User.class`,  `Product.class`, `String.class`
- `TypeReference<T>` -> dùng cho dạng danh sách `List<User>`