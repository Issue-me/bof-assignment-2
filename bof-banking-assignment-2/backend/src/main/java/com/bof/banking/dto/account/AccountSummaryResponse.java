package com.bof.banking.dto.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for account summary statistics (admin overview).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {
    private long totalAccounts;
    private long activeAccounts;
    private long inactiveAccounts;
    private BigDecimal totalSystemBalance;
}
