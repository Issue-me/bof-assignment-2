package com.bof.banking.dto.account;

import com.bof.banking.model.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating/updating accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    private String accountName;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    private BigDecimal initialDeposit;
}
