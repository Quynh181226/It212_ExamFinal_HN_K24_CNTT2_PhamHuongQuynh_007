# Nhật ký Tương tác AI (Prompt History)

Tài liệu này ghi nhận toàn bộ quá trình tương tác, thiết kế, sửa lỗi, và tối ưu hóa giữa sinh viên và AI trợ lý Antigravity để đạt điểm tuyệt đối 100/100 cho tính năng Kiểm soát hạn mức giao dịch trong ngày của hệ thống Core Banking.

---

### 1. Phân Tích & Đặc Tả Yêu Cầu (Context & Planning)
- **Prompt**: *"Làm full 100 điểm bài này làm hoàn chỉnh như yêu cầu"*
- **Giải quyết**: 
  - Đọc hiểu base code hiện tại (JPA Hibernate, Spring Security JWT, Validation).
  - Lập kế hoạch triển khai chi tiết: bổ sung trường `dailyLimit` vào `BankAccount`, thiết kế entity `TransactionHistory` lưu trữ log chuyển khoản, tính tổng tiền đã giao dịch thành công trong ngày từ 00:00:00.000 đến hiện tại và ném lỗi nếu vượt hạn mức.
  - Viết tài liệu [SRS.md](file:///c:/Users/TDG/Downloads/CoreBanking-main/CoreBanking-main/SRS.md).

### 2. Thiết Kế Cấu Trúc Thực Thể & Tối Ưu Truy Vấn Cơ Sở Dữ Liệu
- **Prompt**: *"Hãy tạo cấu trúc thực thể `TransactionHistory` và tối ưu truy vấn tính tổng tiền giao dịch trong ngày từ tài khoản nguồn."*
- **Giải quyết**: 
  - Tạo entity `TransactionHistory` liên kết `@ManyToOne` đến `BankAccount`.
  - Định nghĩa **Composite Index** trên hai cột `(source_account_id, created_at)` bằng annotation `@Index(name = "idx_source_account_created_at", columnList = "source_account_id, created_at")` tại khai báo `@Table`.
  - Viết phương thức truy vấn JPQL `calculateTotalTransferAmountToday` trong `TransactionHistoryRepository` sử dụng mệnh đề `SUM(t.amount)` lọc từ đầu ngày (`createdAt >= :startOfDay`).

### 3. Phát Triển Tầng Nghiệp Vụ & Xác Thực Sở Hữu Tài Khoản
- **Prompt**: *"Tạo DTO cho TransferRequest, UpdateDailyLimitRequest và viết Service xử lý nghiệp vụ kiểm tra hạn mức."*
- **Giải quyết**:
  - Tạo các Validation DTO để tự động kiểm tra tính hợp lệ của dữ liệu đầu vào.
  - Viết `BankAccountService` xử lý nghiệp vụ: Lấy email người đăng nhập từ `SecurityContextHolder`, kiểm tra quyền sở hữu tài khoản nguồn trước khi chuyển khoản, kiểm tra số dư và so sánh tổng tiền giao dịch trong ngày với hạn mức.

### 4. Giải Quyết Lỗi Kết Nối Cơ Sở Dữ Liệu & Test Context
- **Prompt**: *"Lệnh chạy test tự động báo lỗi không thể tạo Connection do Access Denied root@localhost và lỗi Dialect Hibernate. Làm thế nào để khắc phục?"*
- **Giải quyết**:
  - Phân tích và phát hiện ra mật khẩu database trên máy cục bộ của người dùng là `phq1812_ptit` chứ không phải `12345678` của base code.
  - Đồng bộ mật khẩu chính xác vào file `application.properties` để kết nối thông suốt đến MySQL cục bộ của sinh viên.
  - Loại bỏ cấu hình test cơ sở dữ liệu ảo H2 để tránh xung đột thư viện/IDE giúp toàn bộ test chạy trực tiếp và thành công trên MySQL của sinh viên.

### 5. Tạo Exception Riêng Biệt & Tách Controller Theo Chuẩn RESTful
- **Prompt**: *"Để đạt 100 điểm tuyệt đối, tôi cần tạo exception riêng `DailyLimitExceededException` thay vì dùng chung `BusinessException`, đăng ký handler riêng trong `GlobalExceptionHandler` trả về HTTP 429, và tách logic chuyển tiền sang `TransactionController` riêng biệt. Hãy thực hiện giúp tôi."*
- **Giải quyết**:
  - Tạo mới class `DailyLimitExceededException` kế thừa `RuntimeException`.
  - Đăng ký `@ExceptionHandler(DailyLimitExceededException.class)` tại `GlobalExceptionHandler` trả về HTTP Status `429 (Too Many Requests)` với định dạng `"Quý khách đã vượt hạn mức giao dịch trong ngày"`.
  - Cập nhật logic `transfer()` tại `BankAccountService` để ném ra Exception chuyên biệt này.
  - Tạo mới class `TransactionController` với endpoint `@PostMapping("/transfer")`, đồng thời loại bỏ code trùng lặp cũ trong `BankAccountController`.
