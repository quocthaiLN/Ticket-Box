# Đặc tả: AI Artist Bio

## Mục tiêu

Organizer/Admin gửi PDF press kit để tạo bản nháp Artist Bio bất đồng bộ. Module `artistbio` sở hữu job, storage, RabbitMQ và worker; Catalog cung cấp concert qua quan hệ hiện có và đặt hai HTTP route trong `ConcertController`.

## API

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| `POST` | `/admin/concerts/{concertId}/artist-bio-jobs` | Nhận multipart field `file`, trả `202` và thông tin job. |
| `GET` | `/admin/concerts/{concertId}/artist-bio-jobs/{jobId}` | Poll trạng thái và bản nháp; response không bao gồm file gốc hoặc extracted text. |

Chỉ Admin hoặc Organizer sở hữu concert được truy cập. Upload hợp lệ khi concert ở `DRAFT` hoặc `PUBLISHED`; polling vẫn cho phép khi concert đã đóng. File phải là PDF tối đa 10 MiB.

Trạng thái theo schema: `PENDING → PROCESSING → DONE | FAILED`. Upload tạo job `PENDING`. Bio `DONE` chỉ là bản nháp; người tổ chức duyệt rồi cập nhật `concerts.artist_bio` bằng API sửa concert hiện có.

## Xử lý nền

1. API kiểm tra quyền và PDF, upload file vào MinIO, lưu URI `s3://<bucket>/press-kits/<concertId>/<objectId>.pdf`, tạo job và audit `CREATE_ARTIST_BIO_JOB`.
2. API gửi message chỉ chứa `jobId` vào `artist-bio.exchange` / `q.artist.bio-generation`. Bản ghi `PENDING` được giữ để scheduler worker có thể khôi phục nếu broker tạm lỗi.
3. Worker claim job bằng transaction, số lần thử và lease token; tải PDF, giới hạn 50 trang và 30.000 ký tự, trích xuất bằng PDFBox.
4. Gemini `generateContent` sinh JSON `{ "bio": "..." }`. Bio yêu cầu tiếng Việt khoảng 150–200 từ và dựa trên press kit. Kết quả lưu trong `artist_bio_jobs.generated_bio`, job chuyển `DONE`; không tự publish vào concert.
5. Worker ACK sau khi DB commit. Lỗi tạm thời retry tối đa 3 lần qua queue TTL 30 giây; lỗi vĩnh viễn hoặc hết lượt chuyển `FAILED`. Scheduler mỗi 30 giây phục hồi job đến hạn hoặc lease đã hết.

## Cấu hình và giới hạn

- Gemini dùng `GEMINI_API_KEY`; model `GEMINI_MODEL`, mặc định `gemini-2.5-flash`.
- Upload/request giới hạn 10/11 MiB tại Servlet và 11 MiB tại Nginx.
- Message dùng exchange, queue, retry queue và DLX/DLQ riêng của Artist Bio; listener dùng manual ACK.
- Migration V4 thêm attempts, lịch retry, processing token và lease; V1 không sửa đổi.
- Bio thủ công tiếp tục đi qua Catalog để audit và invalidate cache.

## Lỗi và tiêu chí nghiệm thu

- Upload sai định dạng/trống: `400`; vượt dung lượng: `413`; không có quyền: `403`; không thấy concert/job: `404`; concert không nhận upload: `409`; storage không khả dụng: `503`.
- PDF hỏng, mã hóa, scan không có text, Gemini từ chối hoặc output rỗng: job `FAILED` với thông báo an toàn; timeout, rate limit và lỗi server Gemini có thể retry.
- Mọi retry/redelivery chỉ được ghi kết quả nếu processing token còn hiệu lực. Job `DONE` không bị chạy lại; worker không cập nhật `concerts.artist_bio`.
- Test tự động dùng mock Gemini; triển khai cần RabbitMQ, PostgreSQL, MinIO và cấu hình `GEMINI_API_KEY`.
