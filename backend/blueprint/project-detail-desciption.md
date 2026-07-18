# TicketBox

## Bối cảnh

Các concert âm nhạc lớn tại Việt Nam — như Anh Trai Say Hi, Anh Trai Vượt Ngàn Chông Gai, Em Xinh Say Hi, Chị Đẹp Đạp Gió Rẽ Sóng — thu hút hàng chục nghìn khán giả. Khi ban tổ chức mở bán vé, website thường sập trong vài phút đầu do lượng truy cập đồng thời quá lớn; khán giả bị trừ tiền nhưng không nhận được vé; scalper dùng bot mua hết vé trong vài giây rồi bán lại với giá gấp nhiều lần.

Hiện tại nhiều sự kiện vẫn bán vé qua các kênh rời rạc: Zalo OA, Google Form, chuyển khoản thủ công — không đảm bảo tính công bằng và rất dễ xảy ra gian lận.

Công ty tổ chức sự kiện muốn xây dựng hệ thống TicketBox để số hóa toàn bộ quy trình bán vé, từ lúc mở bán đến khi khán giả vào cổng sự kiện.

---

## Người dùng

| Nhóm            | Mô tả                                                                     |
| --------------- | ------------------------------------------------------------------------- |
| Khán giả        | Xem thông tin concert, mua vé, nhận e-ticket, check-in tại cổng           |
| Ban tổ chức     | Tạo và quản lý concert, cấu hình loại vé, theo dõi doanh thu và lượng bán |
| Nhân sự soát vé | Xác nhận vé tại cổng vào bằng mobile app                                  |

---

## Yêu cầu hệ thống

### Xem và mua vé

Khán giả có thể xem danh sách các concert sắp diễn ra, bao gồm thông tin nghệ sĩ biểu diễn, địa điểm tổ chức, sơ đồ chỗ ngồi (sơ đồ SVG tương tác theo khu: GA, SVIP, VIP, CAT1, CAT2) và số vé còn lại theo thời gian thực cho từng loại.

Khán giả chọn loại vé và số lượng, sau đó tiến hành thanh toán qua cổng thanh toán (VNPAY, MoMo). Sau khi thanh toán thành công, khán giả nhận e-ticket dưới dạng mã QR dùng để vào cổng sự kiện.

Mỗi tài khoản chỉ được mua tối đa một số lượng vé nhất định cho mỗi loại vé, do ban tổ chức cấu hình khi tạo concert (ví dụ: SVIP tối đa 2 vé/tài khoản, CAT1 tối đa 4 vé/tài khoản).

Giới hạn này áp dụng trên toàn bộ các đơn hàng đã thanh toán thành công — khán giả không thể lách bằng cách tạo nhiều đơn hàng nhỏ.

---

### Thông báo

Sau khi mua vé thành công, khán giả nhận thông báo xác nhận qua app và email kèm e-ticket.

Khi concert sắp diễn ra (trước 24 giờ), hệ thống gửi nhắc nhở tự động.

Hệ thống cần được thiết kế để dễ dàng bổ sung kênh thông báo mới (ví dụ: Zalo OA, SMS) trong tương lai mà không cần thay đổi lớn.

---

### Quản trị

Ban tổ chức dùng trang web admin để tạo concert mới, cấu hình các loại vé (tên, giá, số lượng, thời điểm mở bán), cập nhật thông tin, hoặc hủy concert.

Trang admin chỉ dành cho nội bộ và cần kiểm soát truy cập chặt chẽ.

Ba nhóm người dùng có quyền hạn khác nhau:

* Khán giả chỉ có thể xem thông tin và mua vé.
* Ban tổ chức có quyền tạo, sửa, hủy concert và xem thống kê doanh thu.
* Nhân sự soát vé chỉ có quyền truy cập chức năng quét mã QR.

---

### Soát vé tại sự kiện

Nhân sự tại cổng vào dùng mobile app để quét mã QR trên e-ticket của khán giả.

Các địa điểm tổ chức concert lớn (sân vận động, nhà thi đấu) thường có vùng sóng không ổn định khi hàng chục nghìn người tập trung.

App phải cho phép ghi nhận soát vé tạm thời khi không có mạng và tự đồng bộ lại khi kết nối được phục hồi.

---

### AI Artist Bio

Ban tổ chức có thể tải lên file PDF hồ sơ nghệ sĩ hoặc press kit của concert.

Hệ thống tự động xử lý, tách nội dung, làm sạch văn bản và gửi sang mô hình AI để tạo bản giới thiệu ngắn gọn hiển thị trên trang chi tiết concert.

---

### Đồng bộ danh sách khách mời VIP

Một số concert có khu vực Guest List dành cho khách mời của nhãn hàng tài trợ.

Hệ thống quản lý khách mời của nhãn hàng không có API — cách duy nhất là nhận file CSV mà nhãn hàng gửi vào ban đêm trước ngày diễn.

TicketBox cần định kỳ nhập danh sách này để nhân sự soát vé có thể xác nhận khách mời tại cổng VIP.

---

## Các vấn đề cần giải quyết

### Tranh chấp vé

Một số loại vé SVIP của concert Anh Trai Say Hi chỉ có 200 chỗ nhưng có thể có hàng chục nghìn khán giả cố mua cùng lúc ngay khi mở bán.

Hệ thống phải đảm bảo không có hai khán giả nào cùng nhận được vé cuối cùng.

---

### Tải trọng đột biến

Khi concert Chị Đẹp Đạp Gió Rẽ Sóng mở bán, dự kiến khoảng 80.000 người truy cập trong 5 phút đầu, trong đó 70% dồn vào phút đầu tiên.

Hệ thống cần có cơ chế:

* Bảo vệ backend API khỏi bị quá tải.
* Ngăn chặn bot.
* Ngăn client gửi request liên tục.
* Đảm bảo tính công bằng giữa các khán giả thật.

---

### Thanh toán không ổn định

Nếu cổng thanh toán (VNPAY/MoMo) gặp sự cố:

* Khán giả vẫn phải xem được thông tin concert.
* Danh sách vé còn lại vẫn hiển thị bình thường.

Luồng mua vé có phí cần xử lý tình huống thanh toán timeout mà không gây ra trừ tiền hai lần.

Các tính năng không liên quan đến thanh toán vẫn phải hoạt động bình thường khi cổng thanh toán gặp sự cố kéo dài.

---

### Soát vé offline

Nhân sự ở khu vực sóng yếu trong sân vận động vẫn phải soát vé được cho khán giả.

Dữ liệu không được mất khi kết nối trở lại và không được cho phép một vé vào cổng hai lần.

---

### Tích hợp một chiều

Không thể gọi API hệ thống quản lý khách mời của nhãn hàng — chỉ có thể đọc CSV được gửi theo lịch cố định.

Luồng nhập dữ liệu phải xử lý được:

* File lỗi.
* Dữ liệu trùng.
* Không làm gián đoạn hệ thống đang chạy.

---

### Giới hạn vé per-user dưới tải cao

Khi hàng chục nghìn người mua vé cùng lúc, cần đảm bảo giới hạn số vé mỗi tài khoản được áp dụng chính xác.

Không để một người mua vượt quá giới hạn dù gửi nhiều request đồng thời.

Đây là bài toán tương tự tranh chấp chỗ ngồi nhưng ở phạm vi per-user thay vì toàn hệ thống.

---

### Trang chủ và trang chi tiết concert bị quá tải

Trang danh sách concert và trang chi tiết từng concert bị đọc với tần suất rất cao (hàng nghìn lần/giây trong giờ cao điểm) nhưng dữ liệu thay đổi không thường xuyên.

Nếu mỗi request đều truy vấn trực tiếp vào database, hệ thống sẽ không chịu được tải.

Cần có chiến lược cache hợp lý để:

* Giảm tải database.
* Vẫn đảm bảo dữ liệu đủ cập nhật.
* Số vé còn lại phản ánh gần đúng thực tế.
