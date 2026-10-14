# Quyết định kiến trúc 02: Chuẩn hóa Partial Update (API PATCH) bằng JsonNullable

## 1. Quyết định (What is the decision?)
Sử dụng thư viện chuẩn **`org.openapitools:jackson-databind-nullable` (`JsonNullable<T>`)** tích hợp vào Jackson ObjectMapper (`JsonNullableModule`) để quản lý các trường cập nhật từng phần (partial update) cho các DTO phục vụ API PATCH trên toàn hệ thống backend.
- **Tại DTO**: Thay thế cơ chế thủ công `Set<String> suppliedFields` và các setter tùy biến bằng các thuộc tính kiểu `JsonNullable<T> = JsonNullable.undefined()`.
  - Phân biệt rõ 3 trạng thái của một trường trong payload JSON theo RFC 7396:
    1. *Undefined / Omitted*: Không gửi trường trong payload (`!field.isPresent()`) $\rightarrow$ Giữ nguyên giá trị hiện tại.
    2. *Explicit Null*: Gửi `"field": null` (`field.isPresent() && field.get() == null`) $\rightarrow$ Xóa giá trị / đặt về null nếu nghiệp vụ cho phép.
    3. *Valued*: Gửi `"field": "value"` (`field.isPresent() && field.get() != null`) $\rightarrow$ Cập nhật giá trị mới.
- **Tại Service layer**: Giữ nguyên cơ chế cập nhật tường minh (explicit checking) bằng cách kiểm tra `if (request.getField().isPresent())` kết hợp với các logic validate nghiệp vụ (`requireText`, `trimToNull`, ràng buộc thời gian), không sử dụng mapping tự động thiếu kiểm soát để tránh ghi đè ngoài ý muốn.

## 2. Lý do lựa chọn (Why I choose it?)
- **Loại bỏ Boilerplate và Code Mùi**: Trước đây mỗi DTO cần khai báo `Set<String> suppliedFields`, viết đè thủ công từng hàm setter để add tên trường vào Set. Cách làm này dễ gây sai sót (quên add field, gõ sai tên field), làm phình to DTO và khó bảo trì.
- **Đúng chuẩn ngữ nghĩa REST/RFC 7396**: `JsonNullable` là giải pháp tiêu chuẩn trong hệ sinh thái Spring & Jackson để giải quyết bài toán partial update / JSON Merge Patch.
- **Kiểm soát chặt chẽ an toàn nghiệp vụ**: Giữ việc cập nhật tường minh trong Service đảm bảo các điều kiện kiểm tra (invariants, business validation, chống conflict trạng thái) được thực thi nhất quán.

## 3. Các giải pháp khác không lựa chọn và lý do (Other solutions and reasons not chosen)
- **Tiếp tục duy trì `Set<String> suppliedFields` trong DTO**: Thủ công, dễ lỗi, phải tự viết lại toàn bộ setter cho từng thuộc tính, không tận dụng được cơ chế deserialization của Jackson.
- **Tự viết Custom Annotation `@NullableJson`**: Cần tự implement custom Jackson deserializer, type introspector hoặc reflection phức tạp; dễ phát sinh lỗi cạnh tranh và tốn chi phí bảo trì không cần thiết so với thư viện chuẩn đã được kiểm chứng.
- **Dùng MapStruct `@MappingTarget` tự động cho partial update**: Logic cập nhật trong service có nhiều quy tắc phức tạp (`requireText`, kiểm tra trạng thái concert, điều kiện ngày tháng, trim text). Để MapStruct tự động map có thể vô tình ghi đè null hoặc vi phạm các validation invariant.
