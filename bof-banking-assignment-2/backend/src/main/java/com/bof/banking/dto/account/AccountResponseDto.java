package com.bof.banking.dto.account;

import com.bof.banking.model.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for account responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponseDto {
    private Long id;
    private String accountNumber;
    private String accountName;
    private AccountType accountType;
    private BigDecimal balance;
    private BigDecimal interestRate;
    private BigDecimal interestEarned;
    private boolean active;
    private LocalDateTime createdAt;
}
