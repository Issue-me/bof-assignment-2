package com.bof.banking.dto.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for submitting a loan repayment.
 */
@Data
public class LoanRepaymentRequest {

    /** ID of the loan to repay */
    @NotNull(message = "Loan ID is required")
    private Long loanId;

    /** Account to debit the repayment from */
    @NotNull(message = "Source account is required")
    private Long sourceAccountId;

    /**
     * Amount to pay. Must be at least FJD 0.01.
     * Can be more than the scheduled monthly payment (extra goes to principal).
     * Can be less (partial payment — still applied, balance reduced proportionally).
     */
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;
}