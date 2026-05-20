package com.bof.banking.service;

import com.bof.banking.dto.loan.LoanDocumentResponse;
import com.bof.banking.dto.loan.LoanRequest;
import com.bof.banking.dto.loan.LoanResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for loan application and management.
 */
public interface LoanService {

    LoanResponse apply(String userEmail, LoanRequest request);

    List<LoanResponse> getMyLoans(String userEmail);

    LoanResponse getLoanById(String userEmail, Long loanId);

    LoanResponse approveLoan(Long loanId);

    LoanResponse rejectLoan(Long loanId, String reason);

    List<LoanResponse> getAllLoans();

    List<LoanResponse> getPendingLoans();

    /** Admin — update the interest rate on an individual ACTIVE loan. */
    LoanResponse updateLoanInterestRate(Long loanId, BigDecimal newAnnualRate);
}