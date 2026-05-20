package com.bof.banking.dto.transaction;

import com.bof.banking.model.enums.TransferLimitCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents configured and used transfer limits for one category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferCategoryLimitUsageResponse {

    private TransferLimitCategory category;

    private BigDecimal dailyLimit;
    private BigDecimal dailyUsed;
    private BigDecimal dailyRemaining;

    private BigDecimal weeklyLimit;
    private BigDecimal weeklyUsed;
    private BigDecimal weeklyRemaining;

    private BigDecimal monthlyLimit;
    private BigDecimal monthlyUsed;
    private BigDecimal monthlyRemaining;

    private BigDecimal yearlyLimit;
    private BigDecimal yearlyUsed;
    private BigDecimal yearlyRemaining;
}
