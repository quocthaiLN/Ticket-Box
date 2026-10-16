# Task Progress

- File này được sử dụng để theo dõi một task được thực hiện qua nhiều Session.
- Mỗi khi được yêu cầu kết thúc một Session cần tổng kết các thông tin: Goal, Completed, Commits, Files or artifacts updated, Known risk or unresolved issue, Next best step.

## Task Description
- Name: Kế hoạch và Kịch bản Load Test API Public Catalog & Order bằng K6 (Performance Testing)
- Description:
  - Xây dựng tài liệu Test Plan chi tiết tại `k6/performance-testing/testplan.md` cho các API thuộc module Public Catalog và Order.
  - Thiết kế cơ chế sinh dữ liệu thử nghiệm 10.000 người dùng (seed dữ liệu vào DB và xuất file `users.json`).
  - Tối ưu hóa việc sử dụng bộ nhớ RAM cho K6 VUs thông qua `SharedArray`.
  - Lập kịch bản kiểm thử độc lập cho từng endpoint API (Catalog listing, Concert details, Seat map, Ticket types, Inventory, Quota, Order Hold, Order Detail, Payment Creation).
  - Lập kịch bản kiểm thử luồng tích hợp E2E mô phỏng hành vi người dùng trong sự kiện Flash Sale: Đăng nhập -> Xem thông tin / Sơ đồ / Giá vé -> Gửi yêu cầu giữ vé (Hold Order kèm Idempotency-Key) -> Gửi yêu cầu thanh toán (VNPay HMAC URL).
  - Đo đạc và thu thập chỉ số phân vị P50, P95, P99 dưới tải lớn (đỉnh điểm 1.000 VUs).
- Goal: Cung cấp đầy đủ kế hoạch kiểm thử hiệu năng, cấu trúc kịch bản K6, script sinh dữ liệu và kịch bản thực thi hoàn chỉnh phục vụ đo đạc P50/P95/P99.
- Acceptance criteria:
  - [x] Phỏng vấn làm rõ yêu cầu với `interview-me`: Thống nhất kịch bản Flash Sale (1.000 VUs dồn vào 1 sự kiện hot, phân tán nhiều hạng vé), 10k users tải qua `SharedArray`, mục tiêu thuần đo đạc P50/P95/P99 không gán ngưỡng fail.
  - [x] Khởi tạo môi trường task mới, lưu trữ progress task cũ vào `.agents/progress/payment-resilience-progress.md`, cập nhật `PROGRESS.md`.
  - [x] Soạn thảo toàn diện tài liệu Test Plan tại `k6/performance-testing/testplan.md`.
  - [ ] Thiết kế script sinh 10.000 user (seed DB & export `users.json`).
  - [ ] Thiết kế script K6 kiểm thử độc lập từng API.
  - [ ] Thiết kế script K6 kiểm thử luồng tích hợp E2E Flash Sale.
  - [ ] Hướng dẫn chạy kiểm thử, phân tích kết quả và xuất báo cáo P50, P95, P99.

## Session Logs

- Session 001
  - Goal: Khởi động task mới, backup progress cũ, hoàn thành Test Plan chi tiết tại `k6/performance-testing/testplan.md`.
  - Completed:
    - Phỏng vấn làm rõ yêu cầu thành công theo skill `interview-me`: xác định kịch bản Flash Sale cạnh tranh 1 sự kiện, 1.000 VUs đỉnh điểm, cấu trúc đo đạc thuần túy P50/P95/P99.
    - Lưu trữ lịch sử `PROGRESS.md` của task trước vào `.agents/progress/payment-resilience-progress.md`.
    - Tạo mới `PROGRESS.md` định hình mục tiêu, phạm vi và tiêu chí nghiệm thu của bài test hiệu năng K6.
    - Soạn thảo tài liệu Test Plan toàn diện tại `k6/performance-testing/testplan.md`.
  - Commits: Đang thực hiện.
  - Files or artifacts updated:
    - `.agents/progress/payment-resilience-progress.md`
    - `PROGRESS.md`
    - `k6/performance-testing/testplan.md`
  - Known risk or unresolved issue: Cần chú ý cấu hình Rate Limit của hệ thống khi chạy tải lớn để tránh bị HTTP 429 ngoài ý muốn.
  - Next best step: Triển khai script sinh dữ liệu 10k users và các file kịch bản K6.
