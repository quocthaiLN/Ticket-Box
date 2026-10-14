# PROJECT DECISIONS

- File này được dùng để ghi lại những quyết định quan trọng được đưa ra trong quá trình thực hiện project.
- Mỗi khi người dùng đưa ra một desision cần ghi nhận lại: What is the desions? Why I choose it? Others solution but I don't choose and reason.
- File này nằm mục đích reference và liệt kê, nội dung đầy đủ được đặt tại [Details of DECISIONS](.agents/DECISIONS/)

## Definition of desision must be recorded
- Liên quan đến kiến trúc cache, exception, message queue,...
- Liên quan đến luồng xử lý quan trọng thanh toán, đặt chỗ, xem vé,...
- Những quyết định có thể sẽ gặp trong tương lai
- Những quyết định không phải inspect code sẽ có thể hiểu được

## List of DECISIONS

| Number | brief | Link |
| :---: | :--- | :--- |
| 01 | Chuẩn hóa chuyển đổi đối tượng Entity <-> DTO bằng MapStruct | [Link](.agents/DECISIONS/01-mapstruct-object-mapping.md) |
| 02 | Chuẩn hóa Partial Update (API PATCH) bằng JsonNullable | [Link](.agents/DECISIONS/02-json-nullable-partial-update.md) |
| 03 | Cơ chế Audit Log hai nhóm sự kiện và đảm bảo tính tin cậy tuyệt đối | [Link](.agents/DECISIONS/03-audit-logging-dual-event-categories.md) |
| 04 | Kiến trúc thanh toán chống lỗi: Tách biệt Payment-Events-Refund, hội tụ xử lý kết quả và xử lý trễ hạn giữ chỗ | [Link](.agents/DECISIONS/04-payment-resilience-events-and-refunds.md) |
