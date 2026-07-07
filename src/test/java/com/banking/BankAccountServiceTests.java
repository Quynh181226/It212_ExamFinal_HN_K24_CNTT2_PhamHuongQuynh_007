package com.banking;

import com.banking.exceptions.BusinessException;
import com.banking.models.constant.CustomerStatus;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.Customer;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.CustomerRepository;
import com.banking.models.repositories.TransactionHistoryRepository;
import com.banking.models.services.BankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class BankAccountServiceTests {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransactionHistoryRepository transactionHistoryRepository;

    private Customer testCustomer;
    private BankAccount sourceAccount;
    private BankAccount destinationAccount;

    @BeforeEach
    void setUp() {
        // Clear repositories
        transactionHistoryRepository.deleteAll();
        bankAccountRepository.deleteAll();
        customerRepository.deleteAll();

        // 1. Create a customer
        testCustomer = Customer.builder()
                .fullName("Nguyen Van A")
                .email("testuser@gmail.com")
                .password("encodedpassword")
                .phoneNumber("0987654321")
                .identityNumber("123456789012")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .address("Hanoi")
                .status(CustomerStatus.ACTIVE)
                .role("CUSTOMER")
                .build();
        testCustomer = customerRepository.save(testCustomer);

        // Mock authentication context for this customer
        UserDetails userDetails = new User(testCustomer.getEmail(), testCustomer.getPassword(), Collections.emptyList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 2. Create source account owned by the customer
        sourceAccount = BankAccount.builder()
                .accountNumber("SOURCE123")
                .balance(new BigDecimal("100000000.0000"))
                .currency("VND")
                .accountType(BankAccount.AccountType.CHECKING)
                .status(BankAccount.AccountStatus.ACTIVE)
                .customer(testCustomer)
                .dailyLimit(new BigDecimal("50000000.0000"))
                .build();
        sourceAccount = bankAccountRepository.save(sourceAccount);

        // 3. Create destination account
        destinationAccount = BankAccount.builder()
                .accountNumber("DEST456")
                .balance(new BigDecimal("1000000.0000"))
                .currency("VND")
                .accountType(BankAccount.AccountType.CHECKING)
                .status(BankAccount.AccountStatus.ACTIVE)
                .customer(testCustomer) // owned by customer as well for testing ease
                .dailyLimit(new BigDecimal("50000000.0000"))
                .build();
        destinationAccount = bankAccountRepository.save(destinationAccount);
    }

    @Test
    void testTransferSuccessWithinLimit() {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber("SOURCE123")
                .destinationAccountNumber("DEST456")
                .amount(new BigDecimal("20000000.0000"))
                .build();

        TransactionHistory tx = bankAccountService.transfer(request);

        assertNotNull(tx);
        assertEquals(new BigDecimal("20000000.0000"), tx.getAmount());
        assertEquals("DEST456", tx.getDestinationAccountNumber());
        assertEquals("SOURCE123", tx.getSourceAccount().getAccountNumber());

        // Verify updated balances
        BankAccount updatedSource = bankAccountRepository.findByAccountNumber("SOURCE123").orElseThrow();
        BankAccount updatedDest = bankAccountRepository.findByAccountNumber("DEST456").orElseThrow();

        // 100,000,000 - 20,000,000 = 80,000,000
        assertEquals(0, new BigDecimal("80000000.0000").compareTo(updatedSource.getBalance()));
        // 1,000,000 + 20,000,000 = 21,000,000
        assertEquals(0, new BigDecimal("21000000.0000").compareTo(updatedDest.getBalance()));
    }

    @Test
    void testTransferFailsExceedingLimit() {
        TransferRequest request1 = TransferRequest.builder()
                .sourceAccountNumber("SOURCE123")
                .destinationAccountNumber("DEST456")
                .amount(new BigDecimal("30000000.0000"))
                .build();

        // First transfer: 30 million (Limit is 50 million)
        bankAccountService.transfer(request1);

        TransferRequest request2 = TransferRequest.builder()
                .sourceAccountNumber("SOURCE123")
                .destinationAccountNumber("DEST456")
                .amount(new BigDecimal("21000000.0000")) // Total will be 51 million (> 50 million limit)
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            bankAccountService.transfer(request2);
        });

        assertEquals(429, exception.getCode());
        assertEquals("Quý khách đã vượt hạn mức giao dịch trong ngày", exception.getMessage());
    }

    @Test
    void testUpdateDailyLimit() {
        UpdateDailyLimitRequest request = UpdateDailyLimitRequest.builder()
                .dailyLimit(new BigDecimal("75000000.0000"))
                .build();

        BankAccount updated = bankAccountService.updateDailyLimit("SOURCE123", request);

        assertNotNull(updated);
        assertEquals(0, new BigDecimal("75000000.0000").compareTo(updated.getDailyLimit()));

        BankAccount fetched = bankAccountRepository.findByAccountNumber("SOURCE123").orElseThrow();
        assertEquals(0, new BigDecimal("75000000.0000").compareTo(fetched.getDailyLimit()));
    }

    @Test
    void testTransferFailsWhenNotOwner() {
        // Create another customer
        Customer anotherCustomer = Customer.builder()
                .fullName("Nguyen Van B")
                .email("otheruser@gmail.com")
                .password("otherpassword")
                .phoneNumber("0987654322")
                .identityNumber("123456789013")
                .dateOfBirth(LocalDate.of(1996, 1, 1))
                .address("Danang")
                .status(CustomerStatus.ACTIVE)
                .role("CUSTOMER")
                .build();
        anotherCustomer = customerRepository.save(anotherCustomer);

        // Change security context to B
        UserDetails userDetails = new User(anotherCustomer.getEmail(), anotherCustomer.getPassword(), Collections.emptyList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber("SOURCE123") // SOURCE123 is owned by A, but currently logged in as B
                .destinationAccountNumber("DEST456")
                .amount(new BigDecimal("5000.0000"))
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            bankAccountService.transfer(request);
        });

        assertEquals(403, exception.getCode());
        assertEquals("You do not own this account", exception.getMessage());
    }
}
