# SRS - Kiểm Soát Hạn Mức và Cảnh Báo Chuyển Khoản

Tài liệu Đặc tả Yêu cầu Hệ thống (SRS) này mô tả thiết kế kỹ thuật, thuật toán và giải pháp lưu trữ để thực hiện tính năng kiểm soát hạn mức giao dịch trong ngày cho luồng chuyển tiền (Core Banking).

---

## 1. Phân Tích Nghiệp Vụ & Yêu Cầu

### 1.1. Các Phát Biểu Nghiệp Vụ chính
- Mỗi tài khoản thanh toán (`BankAccount`) có cấu hình biến `dailyLimit` (Hạn mức chuyển tiền trong ngày). Giá trị mặc định ban đầu là **50.000.000 VND**.
- Khi khách hàng gọi API chuyển khoản, hệ thống tự động tính tổng tiền đã chuyển khoản thành công từ tài khoản nguồn trong ngày hôm nay (từ `00:00:00.000` của ngày hiện tại cho đến thời điểm hiện tại).
- Nếu giao dịch hiện tại làm tổng tiền vượt quá `dailyLimit`, hệ thống chặn lại và ném ra Exception.
- Exception ném ra khi vượt hạn mức sẽ được xử lý bởi Global Exception Handler và trả về HTTP Status Code `429 (Too Many Requests)` (hoặc `403 Forbidden`) đi kèm với thông điệp: `"Quý khách đã vượt hạn mức giao dịch trong ngày"`.
- Hỗ trợ API cập nhật hạn mức ngày (`dailyLimit`) cho tài khoản thuộc sở hữu của chính khách hàng đã đăng nhập.

---

## 2. Thiết Kế Cấu Trúc Lưu Trữ (Database Design)

Để lưu trữ lịch sử chuyển khoản phục vụ cho việc tính tổng số tiền giao dịch trong ngày, ta thiết kế thực thể `TransactionHistory` liên kết với `BankAccount`.

### 2.1. Lược đồ Cơ sở Dữ liệu
- **Bảng `bank_accounts`**:
  - Thêm trường `daily_limit` (Kiểu dữ liệu `DECIMAL(19, 4)` / `BigDecimal` trong Java để tránh sai số thập phân).
- **Bảng `transaction_histories`** (Lưu lịch sử chuyển khoản từ tài khoản nguồn):
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `source_account_id` (BIGINT, Foreign Key liên kết đến `bank_accounts.id`)
  - `destination_account_number` (VARCHAR(50), Số tài khoản đích nhận tiền)
  - `amount` (DECIMAL(19, 4), Số tiền chuyển khoản)
  - `created_at` (DATETIME, Thời điểm thực hiện giao dịch)

### 2.2. Chiến Lược Tối Ưu Hóa Truy Vấn
Để tối ưu hóa hàm tính `SUM()` số tiền giao dịch trong ngày hôm nay, ta thêm **Composite Index** trên 2 trường cột `(source_account_id, created_at)` của bảng `transaction_histories`.

```sql
CREATE INDEX idx_source_account_created_at ON transaction_histories (source_account_id, created_at);
```

**Lý do tối ưu**:
Khi thực hiện câu lệnh truy vấn lọc theo tài khoản và ngày hiện tại:
`WHERE source_account_id = :accountId AND created_at >= :startOfDay`
Hệ thống sẽ thực hiện quét dải chỉ mục (Index Range Scan) trực tiếp trên khóa index ghép mà không phải quét toàn bộ bảng (Table Scan), giúp tốc độ truy vấn đạt độ phức tạp $O(\log N)$ thay vì $O(N)$.

---

## 3. Thuật Toán Tính Tổng & Kiểm Tra Hạn Mức (Pseudo-code)

### Thuật toán bằng Mã giả (Pseudo-code)

```text
Function processTransfer(sourceAccountNumber, destinationAccountNumber, amount):
    // 1. Xác thực tài khoản nguồn và người sở hữu
    customer = getAuthenticatedCustomer()
    sourceAccount = getBankAccountByNumber(sourceAccountNumber)
    If sourceAccount IS NULL Then:
        Throw Exception(404, "Source account not found")
        
    If sourceAccount.ownerId != customer.id Then:
        Throw Exception(403, "You do not own this account")

    // 2. Xác thực tài khoản đích
    destinationAccount = getBankAccountByNumber(destinationAccountNumber)
    If destinationAccount IS NULL Then:
        Throw Exception(404, "Destination account not found")

    // 3. Kiểm tra số dư khả dụng
    If sourceAccount.balance < amount Then:
        Throw Exception(400, "Insufficient balance")

    // 4. Lấy mốc thời gian bắt đầu ngày hôm nay (00:00:00)
    startOfDayToday = getStartOfToday() // e.g. LocalDate.now().atStartOfDay()

    // 5. Truy vấn cơ sở dữ liệu để lấy tổng tiền đã chuyển hôm nay từ tài khoản nguồn
    totalTransferredToday = queryDatabase(
        "SELECT SUM(t.amount) 
         FROM transaction_histories t 
         WHERE t.source_account_id = :sourceAccountId 
           AND t.created_at >= :startOfDayToday"
    )
    
    If totalTransferredToday IS NULL Then:
        totalTransferredToday = 0

    // 6. Kiểm tra điều kiện hạn mức
    If totalTransferredToday + amount > sourceAccount.dailyLimit Then:
        Throw Exception(429, "Quý khách đã vượt hạn mức giao dịch trong ngày")

    // 7. Thực hiện trừ/cộng tiền
    sourceAccount.balance = sourceAccount.balance - amount
    destinationAccount.balance = destinationAccount.balance + amount
    saveAccountState(sourceAccount)
    saveAccountState(destinationAccount)

    // 8. Ghi nhận log lịch sử giao dịch thành công
    transactionHistory = createTransactionHistoryRecord(
        sourceAccount = sourceAccount,
        destinationAccountNumber = destinationAccountNumber,
        amount = amount,
        createdAt = getCurrentDateTime()
    )
    saveTransactionHistory(transactionHistory)

    Return transactionHistory
```
