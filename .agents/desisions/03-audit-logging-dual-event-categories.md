# Quyết định kiến trúc 03: Cơ chế Audit Log hai nhóm sự kiện và đảm bảo tính tin cậy tuyệt đối

## 1. Quyết định (What is the decision?)

Thiết lập cơ chế kiểm toán (Audit Trail) tập trung qua `AuditLogService` trên bảng `audit_logs`, tuân thủ 7 nguyên tắc thiết kế bất biến:

1. **Mục tiêu**: Bảng `audit_logs` là audit trail chính thức phục vụ Quản trị viên (Admin) điều tra sự cố và đối soát. Hệ thống cam kết độ tin cậy tuyệt đối: không để sót thay đổi nghiệp vụ thành công và không ghi nhận sự kiện chưa từng diễn ra.
2. **Hai loại sự kiện, hai cơ chế Transaction**:
   - *Thay đổi nghiệp vụ thành công (Tạo, sửa, hủy concert, đổi role, trạng thái user, ...)*: Ghi trong **cùng transaction** với thay đổi đó (`Propagation.REQUIRED`). Thao tác nghiệp vụ và bản ghi log commit hoặc rollback cùng nhau $\rightarrow$ Không có thay đổi nào thiếu dấu vết, và không có dấu vết nào cho thay đổi chưa xảy ra.
   - *Sự kiện thất bại cần điều tra (Đăng nhập sai, cố tình truy cập tài nguyên bị cấm 403, ...)*: Ghi trong **transaction riêng biệt độc lập** (`Propagation.REQUIRES_NEW`) và gọi trước khi ném exception $\rightarrow$ Bản ghi log tồn tại vĩnh viễn dù nghiệp vụ cha rollback.
3. **Ranh giới của `AuditLogService`**: Là điểm duy nhất (Single Point of Entry) để ghi audit log trong hệ thống, phân tách rõ 2 phương thức (`logBusinessSuccess` và `logInvestigativeFailure`) để che giấu hoàn toàn logic transaction propagation đối với caller. Service chỉ đóng vai trò ghi nhận dữ liệu, không can thiệp hay quyết định logic nghiệp vụ.
4. **Quy tắc xử lý lỗi**:
   - *Với nghiệp vụ thành công*: **Không nuốt lỗi**. Nếu ghi log thất bại (lỗi DB, constraint), ngoại lệ lan truyền ra ngoài làm rollback toàn bộ thay đổi nghiệp vụ cha ("không có thay đổi vô hình").
   - *Với sự kiện thất bại điều tra*: Nuốt lỗi an toàn kèm ghi log cảnh báo ra hệ thống (`logger.error(...)`) trong trường hợp hiếm gặp khi DB audit lỗi, đảm bảo không làm che giấu hay gián đoạn exception bảo mật ban đầu ném về phía client.
5. **Định danh sự kiện tập trung**: Toàn bộ hành động kiểm toán dùng enum tập trung `AuditAction`, nghiêm cấm sử dụng chuỗi ký tự tự do (string literals).
6. **Bảo vệ dữ liệu & Tự động trích xuất ngữ cảnh**:
   - Nghiêm cấm lưu mật khẩu, secret key, token, OTP, số thẻ tín dụng hoặc PII đầy đủ; tự động làm mờ/che giấu (masking) email và các trường nhạy cảm trong metadata.
   - Actor (`User`) được tự động trích xuất từ `SecurityContextHolder`, Client IP và User-Agent được tự động lấy từ `RequestContextHolder` (ưu tiên header `X-Forwarded-For`). Caller không cần và không được phép truyền thủ công các thông số này.
   - Thời gian xử lý chuẩn hóa theo `TIMESTAMPTZ` (UTC / ISO 8601).
7. **Bất biến và quyền truy cập**:
   - Bản ghi `audit_logs` là bất biến (khóa cập nhật ở tầng JPA với `updatable = false` trên 100% cột). Ứng dụng chỉ có quyền `INSERT` và `SELECT`. Tuyệt đối không cung cấp bất kỳ API cập nhật hoặc xóa nào.
   - Chỉ người dùng có vai trò `ADMIN` mới được phép tra cứu API (`/admin/audit-logs`). Mỗi lần Admin truy vấn log cũng được tự động ghi nhận thành sự kiện audit `READ_AUDIT_LOGS` ("Auditing the Auditors").

---

## 2. Lý do lựa chọn (Why I choose it?)

- **Tính toàn vẹn và độ tin cậy tuyệt đối của Audit Trail**: Trong các hệ thống vé và thanh toán tài chính, nếu thao tác nghiệp vụ thành công nhưng ghi log thất bại hoặc ngược lại (nghiệp vụ lỗi nhưng log vẫn ghi thành công), Admin và kiểm toán viên sẽ không thể tái hiện sự thật khi xảy ra tranh chấp. Cơ chế `REQUIRED` cho thành công và `REQUIRES_NEW` cho thất bại điều tra đảm bảo độ tin cậy toán học ở cấp độ CSDL quan hệ.
- **Che giấu độ phức tạp về Transaction Propagation**: Lập trình viên tại các module (`catalog`, `auth`, `artistbio`, ...) không cần phải tự quyết định hay cấu hình annotation `@Transactional(propagation = ...)` mỗi khi cần ghi log. Việc tách 2 method tường minh ở `AuditLogService` giúp code nghiệp vụ ngắn gọn, đúng chuẩn và tránh lỗi vô tình làm hỏng transaction context.
- **Ngăn chặn giả mạo ngữ cảnh (Anti-spoofing)**: Trích xuất `Actor` và `IP/User-Agent` trực tiếp từ container ngữ cảnh xác thực và HTTP request ngăn chặn tình trạng caller cố tình hoặc vô ý truyền sai danh tính người thực hiện hành vi.
- **Tuân thủ quy chuẩn an toàn thông tin & Luật bảo vệ dữ liệu (GDPR/Nghị định 13)**: Việc tự động sanitize/redact mật khẩu, token và mask email trong metadata giúp bảo vệ an toàn cho hệ thống ngay cả khi dữ liệu log được mở cho Admin tra cứu hoặc xuất file.

---

## 3. Các giải pháp khác không lựa chọn và lý do (Other solutions and reasons not chosen)

- **Ghi log bất đồng bộ bằng Spring Event / Message Queue (`@Async` / RabbitMQ)**:
  - *Lý do không chọn*: Nếu service ghi log bất đồng bộ sau khi commit, trường hợp worker/app crash hoặc RabbitMQ mất message sẽ làm mất log nghiệp vụ mà không hề hay biết. Ngược lại, nếu gửi message trước khi commit, nếu transaction nghiệp vụ sau đó bị rollback do constraint, log đã gửi đi vẫn ghi nhận thành công $\rightarrow$ làm ô nhiễm audit trail với những sự kiện "ảo" không có thực trong CSDL.
- **Sử dụng chuỗi ký tự tự do (String literals) cho Action**:
  - *Lý do không chọn*: Dễ gây sai chính tả (`CREATE_CONCERT` vs `CONCERT_CREATE`), khó bảo trì, phân mảnh dữ liệu khi lọc/truy vấn báo cáo và không thể kiểm soát danh mục hành vi tại thời điểm biên dịch (compile-time safety).
- **Để nghiệp vụ / Controller tự truyền thủ công `actor`, `ipAddress`, `userAgent`**:
  - *Lý do không chọn*: Gây ô nhiễm chữ ký phương thức (signature pollution) ở tầng Service, buộc Controller phải trích xuất thủ công các servlet header; dễ bị bỏ quên (truyền null) hoặc bị giả mạo tham số nếu gọi xuyên module.
- **Dùng một hàm `log(...)` duy nhất và để lập trình viên tự truyền cờ boolean hoặc tự bắt ngoại lệ**:
  - *Lý do không chọn*: Gây rủi ro lập trình viên không nắm rõ transaction propagation, có thể vô tình nuốt lỗi ở nghiệp vụ thành công (dẫn tới thay đổi vô hình) hoặc không bọc `REQUIRES_NEW` ở sự kiện thất bại (dẫn tới việc log bị biến mất khi exception ném ra).
- **Cho phép API sửa (`PUT/PATCH`) hoặc xóa (`DELETE`) Audit Log**:
  - *Lý do không chọn*: Vi phạm trực tiếp nguyên tắc bất biến (Immutability) và làm mất hoàn toàn giá trị pháp lý / điều tra của nhật ký kiểm toán.
