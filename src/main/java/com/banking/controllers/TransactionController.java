package com.banking.controllers;

import com.banking.advice.ApiResponse;
import com.banking.models.dto.TransferRequest;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.services.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final BankAccountService bankAccountService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionHistory>> transfer(
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.transfer(request),
                "Transfer completed successfully"));
    }
}
