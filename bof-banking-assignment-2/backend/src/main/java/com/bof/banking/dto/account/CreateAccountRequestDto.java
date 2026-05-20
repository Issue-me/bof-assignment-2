package com.bof.banking.dto.account;

import com.bof.banking.model.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for account creation request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequestDto {

    /**
     * Target CUSTOMER user ID when account is created by teller/admin.
     */
    private Long customerUserId;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    private BigDecimal initialDeposit;

    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String accountName;
}
