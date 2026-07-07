package com.banking.models.services;

import com.banking.exceptions.BusinessException;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.Customer;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.CustomerRepository;
import com.banking.models.repositories.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Transactional
    public TransactionHistory transfer(TransferRequest request) {
        // 1. Get logged-in user email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(404, "Customer not found"));

        // 2. Fetch source bank account
        BankAccount sourceAccount = bankAccountRepository.findByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new BusinessException(404, "Source account not found"));

        // 3. Verify that the logged-in customer owns the source account
        if (!sourceAccount.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessException(403, "You do not own this account");
        }

        // 4. Fetch destination bank account
        BankAccount destinationAccount = bankAccountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new BusinessException(404, "Destination account not found"));

        // 5. Verify source account has sufficient balance
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(400, "Insufficient balance");
        }

        // 6. Calculate total amount transferred today by this source account
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal totalTransferredToday = transactionHistoryRepository
                .calculateTotalTransferAmountToday(sourceAccount, startOfDay);

        // 7. Check if transfer exceeds the daily limit
        BigDecimal totalWithCurrent = totalTransferredToday.add(request.getAmount());
        if (totalWithCurrent.compareTo(sourceAccount.getDailyLimit()) > 0) {
            throw new com.banking.exceptions.DailyLimitExceededException("Quý khách đã vượt hạn mức giao dịch trong ngày");
        }

        // 8. Deduct from source and add to destination
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));

        bankAccountRepository.save(sourceAccount);
        bankAccountRepository.save(destinationAccount);

        // 9. Save to transaction history log
        TransactionHistory transactionHistory = TransactionHistory.builder()
                .sourceAccount(sourceAccount)
                .destinationAccountNumber(destinationAccount.getAccountNumber())
                .amount(request.getAmount())
                .build();

        return transactionHistoryRepository.save(transactionHistory);
    }

    @Transactional
    public BankAccount updateDailyLimit(String accountNumber, UpdateDailyLimitRequest request) {
        // 1. Get logged-in user email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(404, "Customer not found"));

        // 2. Fetch bank account
        BankAccount bankAccount = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(404, "Bank account not found"));

        // 3. Verify ownership
        if (!bankAccount.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessException(403, "You do not own this account");
        }

        // 4. Update the daily limit
        bankAccount.setDailyLimit(request.getDailyLimit());
        return bankAccountRepository.save(bankAccount);
    }
}
