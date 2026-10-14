# Quyết định kiến trúc 01: Chuẩn hóa chuyển đổi đối tượng (Object Mapping) bằng MapStruct

## 1. Quyết định (What is the decision?)
Sử dụng thư viện **MapStruct** (tích hợp Spring Component Model và Lombok Binding) để thực hiện ánh xạ đối tượng giữa Entity và DTO/Record cho toàn bộ hệ thống API backend, bắt đầu với lát cắt mỏng tại module `catalog`.
- **Chiều Entity $\rightarrow$ Response DTO**: Sử dụng MapStruct thay thế hoàn toàn các hàm `private mapTo...` trùng lặp trong các Service.
- **Chiều Request $\rightarrow$ Entity**: Sử dụng MapStruct để ánh xạ các trường đầu vào cơ bản từ `Create*Request` sang Entity; Service giữ vai trò thiết lập các thuộc tính ngữ cảnh nghiệp vụ (`organizer`, `status = DRAFT`, quantity mặc định).
- **Chiều Cập nhật (Update)**: Giữ nguyên cơ chế kiểm tra partial update (`request.wasSupplied(...)`) hiện tại trong Service nhằm tránh rủi ro ghi đè ngoài ý muốn.

## 2. Lý do lựa chọn (Why I choose it?)
- Loại bỏ mã nguồn boilerplate và duplicate: các phương thức `mapTo...` giống hệt nhau trước đó bị lặp lại ở 3 Service khác nhau (`ConcertServiceImpl`, `CatalogServiceImpl`, `ConcertWarmUpServiceImpl`).
- Hiệu năng cao và an toàn kiểu dữ liệu tại thời điểm biên dịch (Compile-time generation): MapStruct sinh code Java thuần tại build time, không sử dụng reflection runtime.
- Dễ kiểm thử và bảo trì: Các mapper được cấu hình làm Spring Component (`@Component`), dễ dàng inject và spy/mock trong Unit Tests.

## 3. Các giải pháp khác không lựa chọn và lý do (Other solutions and reasons not chosen)
- **ModelMapper / ModelMapper Spring**: Dựa trên Java Reflection lúc runtime, hiệu năng thấp hơn, khó debug lỗi mapping khi đổi tên trường, và tiềm ẩn lỗi runtime khó phát hiện.
- **Tiếp tục duy trì mapping thủ công (Manual mapping methods trong Service)**: Dẫn đến mã nguồn bị phình to, lặp code giữa nhiều service cùng module, khó tái sử dụng và vi phạm nguyên lý Single Responsibility.
- **Dùng MapStruct `@MappingTarget` tự động cho cả các hàm Update**: Không chọn vì logic partial update của hệ thống đang phụ thuộc vào kiểm tra `request.wasSupplied("field")`. Để MapStruct tự động map có thể vô tình ghi đè null hoặc vi phạm các validation invariant phức tạp.
