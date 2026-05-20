package com.bof.banking.dto.loan;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for submitting a new loan application.
 * Validated against Reserve Bank of Fiji responsible lending guidelines.
 */
@Data
public class LoanRequest {

    @NotBlank(message = "Loan type is required")
    private String loanType;              // Personal Loan, Home Loan, Vehicle Loan, Business Loan

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "500.00", message = "Minimum loan amount is FJD 500")
    @DecimalMax(value = "500000.00", message = "Maximum loan amount is FJD 500,000")
    private BigDecimal amount;

    @NotNull(message = "Loan term is required")
    @Min(value = 6,   message = "Minimum term is 6 months")
    @Max(value = 300, message = "Maximum term is 300 months (25 years)")
    private Integer termMonths;

    @NotBlank(message = "Loan purpose is required")
    private String purpose;

    @NotNull(message = "Disbursement account is required")
    private Long disbursementAccountId;

    // Employment & income — used for DSR assessment
    @NotBlank(message = "Employment type is required")
    private String employmentType;

    private String employer;

    @NotNull(message = "Monthly income is required")
    @DecimalMin(value = "1.00", message = "Monthly income must be greater than 0")
    private BigDecimal monthlyIncome;

    private BigDecimal otherIncome;
    private BigDecimal existingMonthlyRepayments;
}