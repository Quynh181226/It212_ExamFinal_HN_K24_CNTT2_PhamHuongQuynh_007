# Nhật ký Tương tác AI (Prompt History)

Tài liệu này ghi nhận quá trình tương tác, hướng dẫn và lập kế hoạch để xây dựng tính năng kiểm soát hạn mức giao dịch trong ngày cho hệ thống Core Banking.

---

### 1. Phân Tích & Xác Định Yêu Cầu (Context & Planning)
- **Prompt**: *"Làm full 100 điểm bài này làm hoàn chỉnh như yêu cầu"*
- **Mục tiêu**: Đọc hiểu base code hiện tại (Spring Boot, Spring Security JWT, JPA Hibernate), thiết kế cấu trúc dữ liệu cho thực thể `TransactionHistory`, bổ sung trường `dailyLimit` vào `BankAccount`, cài đặt logic tính tổng tiền đã chuyển khoản hôm nay và so khớp với hạn mức, xử lý lỗi tại `GlobalExceptionHandler` trả về mã lỗi HTTP 429, viết API cập nhật hạn mức và tạo các tài liệu yêu cầu (`SRS.md` và `Prompt_History.md`).

### 2. Thiết Kế Cấu Trúc Thực Thể & Tối Ưu Truy Vấn
- **Prompt**: *"Hãy tạo cấu trúc thực thể `TransactionHistory` và tối ưu truy vấn tính tổng tiền giao dịch trong ngày từ tài khoản nguồn."*
- **Giải pháp**: Xây dựng thực thể `TransactionHistory` liên kết `@ManyToOne` đến tài khoản nguồn, sử dụng cột `created_at` làm mốc thời gian giao dịch. Thêm composite index `@Index(name = "idx_source_account_created_at", columnList = "source_account_id, created_at")` tại khai báo `@Table` của thực thể để đảm bảo tốc độ tính tổng đạt tối ưu.

### 3. Phát Triển Tầng Nghiệp Vụ (Service Layer) & API
- **Prompt**: *"Tạo DTO cho TransferRequest, UpdateDailyLimitRequest và viết Service xử lý nghiệp vụ kiểm tra hạn mức."*
- **Giải pháp**:
  - Viết `TransferRequest` và `UpdateDailyLimitRequest` sử dụng thư viện `jakarta.validation` để kiểm tra tính hợp lệ đầu vào.
  - Viết `BankAccountService` chịu trách nhiệm lấy thông tin người dùng đang đăng nhập thông qua `SecurityContextHolder`, kiểm tra tính sở hữu tài khoản, thực hiện trừ số dư tài khoản nguồn, cộng số dư tài khoản đích, ghi log giao dịch và kiểm tra hạn mức `dailyLimit`.

### 4. Tích Hợp API Endpoint & Global Exception Handler
- **Prompt**: *"Cập nhật BankAccountController và GlobalExceptionHandler để bắt BusinessException ném ra trạng thái lỗi 429."*
- **Giải pháp**:
  - Expose `/api/v1/bankAccounts/transfer` và `/api/v1/bankAccounts/{accountNumber}/dailyLimit` trong `BankAccountController`.
  - Cập nhật handler để trả về HTTP Status Code phù hợp (429/403) kèm theo thông báo cụ thể `"Quý khách đã vượt hạn mức giao dịch trong ngày"`.

### 5. Tạo Các File Bắt Buộc & Kiểm Thử
- **Prompt**: *"Tạo tài liệu SRS.md mô tả đặc tả và mã giả, Prompt_History.md lưu trữ lịch sử lệnh."*
- **Giải pháp**: Viết tài liệu `SRS.md` chi tiết và `Prompt_History.md` trong thư mục gốc của dự án.
