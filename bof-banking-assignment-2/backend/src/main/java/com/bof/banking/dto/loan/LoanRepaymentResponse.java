package com.bof.banking.dto.loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO returned after a successful loan repayment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepaymentResponse {

    private Long       id;
    private String     reference;

    private String     loanNumber;
    private String     loanType;

    /** Account number that was debited */
    private String     sourceAccountNumber;

    private BigDecimal amountPaid;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;

    /** Outstanding balance remaining after this repayment */
    private BigDecimal balanceAfter;

    /** true when this repayment cleared the loan in full */
    private boolean    loanPaidOff;

    private LocalDate  paymentDate;
}