package com.banking.models.repositories;

import com.banking.models.entities.BankAccount;
import com.banking.models.entities.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionHistory t " +
           "WHERE t.sourceAccount = :account " +
           "AND t.createdAt >= :startOfDay")
    BigDecimal calculateTotalTransferAmountToday(
            @Param("account") BankAccount account,
            @Param("startOfDay") LocalDateTime startOfDay
    );
}
