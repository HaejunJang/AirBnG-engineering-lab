package com.airbng.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class WalletWithdrawRequest {
    @NotNull
    Long accountId;
    @NotNull
    @DecimalMin(value = "1000.00")
    private BigDecimal amount;
}
