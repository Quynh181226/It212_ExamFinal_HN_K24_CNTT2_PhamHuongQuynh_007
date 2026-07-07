package com.banking.controllers;

import com.banking.models.entities.BankAccount;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.services.BankAccountService;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.advice.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bankAccounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountRepository bankAccountRepository;
    private final BankAccountService bankAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BankAccount>>> getAllBankAccounts() {
        return ResponseEntity.ok(ApiResponse.success(bankAccountRepository.findAll(),
                "Fetched all bank account successfully"));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionHistory>> transfer(
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.transfer(request),
                "Transfer completed successfully"));
    }

    @PutMapping("/{accountNumber}/dailyLimit")
    public ResponseEntity<ApiResponse<BankAccount>> updateDailyLimit(
            @PathVariable String accountNumber,
            @Valid @RequestBody UpdateDailyLimitRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.updateDailyLimit(accountNumber, request),
                "Daily limit updated successfully"));
    }
}
