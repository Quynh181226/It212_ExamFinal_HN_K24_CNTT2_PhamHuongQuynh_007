package com.banking.models.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDailyLimitRequest {

    @NotNull(message = "Daily limit is required")
    @DecimalMin(value = "0.00", message = "Daily limit must be greater than or equal to zero")
    private BigDecimal dailyLimit;
}
