package com.bof.banking.service;

import com.bof.banking.dto.loan.LoanRepaymentRequest;
import com.bof.banking.dto.loan.LoanRepaymentResponse;

import java.util.List;

/**
 * Get all repayments made against a specific loan — validates ownership.
 */
public interface LoanRepaymentService {

    /** Make a repayment against an active loan. */
    LoanRepaymentResponse makeRepayment(String userEmail, LoanRepaymentRequest request);

    /** Get all repayments made against a specific loan — validates ownership. */
    List<LoanRepaymentResponse> getRepaymentHistory(String userEmail, Long loanId);
}
