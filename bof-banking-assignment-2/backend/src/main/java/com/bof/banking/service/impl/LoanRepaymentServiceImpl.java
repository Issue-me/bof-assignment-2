// package com.bof.banking.service.impl;

// import com.bof.banking.dto.loan.LoanRepaymentRequest;
// import com.bof.banking.dto.loan.LoanRepaymentResponse;
// import com.bof.banking.exception.BadRequestException;
// import com.bof.banking.exception.InsufficientFundsException;
// import com.bof.banking.exception.ResourceNotFoundException;
// import com.bof.banking.exception.UnauthorizedException;
// import com.bof.banking.model.Account;
// import com.bof.banking.model.Loan;
// import com.bof.banking.model.LoanRepayment;
// import com.bof.banking.model.User;
// import com.bof.banking.model.enums.LoanStatus;
// import com.bof.banking.repository.AccountRepository;
// import com.bof.banking.repository.LoanRepaymentRepository;
// import com.bof.banking.repository.LoanRepository;
// import com.bof.banking.repository.UserRepository;
// import com.bof.banking.service.LoanRepaymentService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.List;
// import java.util.UUID;
// import java.util.stream.Collectors;

// /**
//  * Processes loan repayments following standard amortisation rules.
//  *
//  * Uses the project's existing domain exception classes so that the
//  * GlobalExceptionHandler maps them to the correct HTTP status codes:
//  *   ResourceNotFoundException  → 404
//  *   UnauthorizedException      → 401
//  *   InsufficientFundsException → 400
//  *   BadRequestException        → 400
//  *
//  * Previously all throws were plain RuntimeException which the handler
//  * caught and returned as 500 Internal Server Error.
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class LoanRepaymentServiceImpl implements LoanRepaymentService {

//     private static final int        SCALE       = 2;
//     private static final BigDecimal MIN_PAYMENT = new BigDecimal("1.00");

//     private final LoanRepository          loanRepository;
//     private final LoanRepaymentRepository repaymentRepository;
//     private final AccountRepository       accountRepository;
//     private final UserRepository          userRepository;

//     @Override
//     @Transactional
//     public LoanRepaymentResponse makeRepayment(String userEmail, LoanRepaymentRequest req) {

//         // ── Validate user ───────────────────────────────────────────────
//         User user = userRepository.findByEmail(userEmail)
//                 .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

//         // ── Validate loan ───────────────────────────────────────────────
//         Loan loan = loanRepository.findById(req.getLoanId())
//                 .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

//         if (!loan.getUser().getId().equals(user.getId())) {
//             throw new UnauthorizedException("Access denied — this loan does not belong to you");
//         }
//         if (loan.getStatus() != LoanStatus.ACTIVE) {
//             throw new BadRequestException(
//                 "Repayments can only be made on ACTIVE loans. Current status: " + loan.getStatus()
//             );
//         }

//         // ── Validate source account ─────────────────────────────────────
//         Account source = accountRepository.findById(req.getSourceAccountId())
//                 .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

//         if (!source.getUser().getId().equals(user.getId())) {
//             throw new UnauthorizedException("Access denied — this account does not belong to you");
//         }

//         // ── Validate amount ─────────────────────────────────────────────
//         BigDecimal requestedAmount = req.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
//         BigDecimal outstanding     = loan.getOutstandingBalance().setScale(SCALE, RoundingMode.HALF_UP);

//         // Allow amounts below FJD 1.00 only when clearing a residual balance
//         boolean isResidualClearance = outstanding.compareTo(MIN_PAYMENT) < 0
//                 && requestedAmount.compareTo(outstanding) >= 0;

//         if (!isResidualClearance && requestedAmount.compareTo(MIN_PAYMENT) < 0) {
//             throw new BadRequestException(
//                 "Minimum repayment is FJD 1.00. Outstanding balance is " + fmt(outstanding) +
//                 " — pay the full remaining balance to close this loan."
//             );
//         }

//         // Cap at outstanding — customer cannot overpay
//         BigDecimal amountPaid = requestedAmount.min(outstanding);

//         // ── Sufficient funds ────────────────────────────────────────────
//         if (source.getBalance().compareTo(amountPaid) < 0) {
//             throw new InsufficientFundsException(String.format(
//                 "Insufficient funds. Available: FJD %.2f, Repayment: FJD %.2f",
//                 source.getBalance(), amountPaid
//             ));
//         }

//         // ── Principal / interest split ──────────────────────────────────
//         BigDecimal monthlyRate = loan.getInterestRate()
//                 .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
//         BigDecimal interestDue = outstanding.multiply(monthlyRate)
//                 .setScale(SCALE, RoundingMode.HALF_UP);

//         BigDecimal interestComponent  = amountPaid.min(interestDue);
//         BigDecimal principalComponent = amountPaid.subtract(interestComponent)
//                 .max(BigDecimal.ZERO)
//                 .setScale(SCALE, RoundingMode.HALF_UP);
//         BigDecimal newBalance = outstanding.subtract(principalComponent)
//                 .max(BigDecimal.ZERO)
//                 .setScale(SCALE, RoundingMode.HALF_UP);

//         boolean paidOff = newBalance.compareTo(BigDecimal.ZERO) == 0;

//         // ── Persist repayment ───────────────────────────────────────────
//         String reference = "REP-" + LocalDate.now().getYear()
//                 + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

//         LoanRepayment repayment = LoanRepayment.builder()
//                 .reference(reference)
//                 .loan(loan)
//                 .sourceAccount(source)
//                 .amountPaid(amountPaid)
//                 .principalComponent(principalComponent)
//                 .interestComponent(interestComponent)
//                 .balanceAfter(newBalance)
//                 .paymentDate(LocalDate.now())
//                 .build();

//         repaymentRepository.save(repayment);

//         // ── Update loan ─────────────────────────────────────────────────
//         loan.setOutstandingBalance(newBalance);
//         if (paidOff) {
//             loan.setStatus(LoanStatus.PAID_OFF);
//             log.info("Loan fully repaid: loanNumber={} user={}", loan.getLoanNumber(), userEmail);
//         }
//         loanRepository.save(loan);

//         // ── Debit source account ────────────────────────────────────────
//         source.setBalance(source.getBalance().subtract(amountPaid));
//         accountRepository.save(source);

//         log.info("Repayment processed: ref={} loan={} paid={} principal={} interest={} newBalance={}",
//                 reference, loan.getLoanNumber(), amountPaid,
//                 principalComponent, interestComponent, newBalance);

//         return toResponse(repayment, paidOff);
//     }

//     @Override
//     @Transactional(readOnly = true)
//     public List<LoanRepaymentResponse> getRepaymentHistory(String userEmail, Long loanId) {
//         User user = userRepository.findByEmail(userEmail)
//                 .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

//         Loan loan = loanRepository.findById(loanId)
//                 .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

//         if (!loan.getUser().getId().equals(user.getId())) {
//             throw new UnauthorizedException("Access denied");
//         }

//         return repaymentRepository.findByLoanWithAccount(loan)
//                 .stream()
//                 .map(r -> toResponse(r, false))
//                 .collect(Collectors.toList());
//     }

//     // ── Private helpers ────────────────────────────────────────────────

//     private LoanRepaymentResponse toResponse(LoanRepayment r, boolean paidOff) {
//         Loan    loan   = r.getLoan();
//         Account source = r.getSourceAccount();
//         return LoanRepaymentResponse.builder()
//                 .id(r.getId())
//                 .reference(r.getReference())
//                 .loanNumber(loan   != null ? loan.getLoanNumber()           : null)
//                 .loanType  (loan   != null ? loan.getLoanType()             : null)
//                 .sourceAccountNumber(source != null ? source.getAccountNumber() : null)
//                 .amountPaid        (r.getAmountPaid())
//                 .principalComponent(r.getPrincipalComponent())
//                 .interestComponent (r.getInterestComponent())
//                 .balanceAfter      (r.getBalanceAfter())
//                 .loanPaidOff       (paidOff)
//                 .paymentDate       (r.getPaymentDate())
//                 .build();
//     }

//     private String fmt(BigDecimal v) {
//         return String.format("FJD %.2f", v == null ? BigDecimal.ZERO : v);
//     }
// }

package com.bof.banking.service.impl;

import com.bof.banking.dto.loan.LoanRepaymentRequest;
import com.bof.banking.dto.loan.LoanRepaymentResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.InsufficientFundsException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.exception.UnauthorizedException;
import com.bof.banking.model.Account;
import com.bof.banking.model.Loan;
import com.bof.banking.model.LoanRepayment;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.LoanStatus;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.LoanRepaymentRepository;
import com.bof.banking.repository.LoanRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.LoanRepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Processes loan repayments.
 *
 * Bank account flows:
 *   1. Debit customer's source account by amountPaid
 *   2. Credit BANK_INTERNAL_OPERATIONS by amountPaid
 *   3. Write a LOAN_REPAYMENT Transaction record (visible in account history)
 *   4. If outstandingBalance == 0 → set loan status to CLOSED (not PAID_OFF)
 *      per the status lifecycle: ACTIVE → CLOSED when fully repaid.
 *
 * Note: PAID_OFF is preserved as an alias in the enum for backward compat
 * but CLOSED is the canonical terminal state per RBF conventions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRepaymentServiceImpl implements LoanRepaymentService {

    private static final int        SCALE       = 2;
    private static final BigDecimal MIN_PAYMENT = new BigDecimal("1.00");

    @Value("${app.bank.internal-account-number:BOF90000001}")
    private String bankInternalAccountNumber;

    private final LoanRepository          loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final AccountRepository       accountRepository;
    private final UserRepository          userRepository;
    private final TransactionRepository   transactionRepository;

    @Override
    @Transactional
    /**
     * Handles make repayment.
     * @param userEmail the email of the authenticated user.
     * @param req the request payload.
     * @return the result of the operation.
     */
    public LoanRepaymentResponse makeRepayment(String userEmail, LoanRepaymentRequest req) {

        // ── Validate user ─────────────────────────────────────────────────
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // ── Validate loan ─────────────────────────────────────────────────
        Loan loan = loanRepository.findById(req.getLoanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if (!loan.getUser().getId().equals(user.getId()))
            throw new UnauthorizedException("Access denied — this loan does not belong to you");

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BadRequestException(
                "Repayments can only be made on ACTIVE loans. Current status: " + loan.getStatus());

        // ── Validate source account ───────────────────────────────────────
        Account source = accountRepository.findById(req.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        if (!source.getUser().getId().equals(user.getId()))
            throw new UnauthorizedException("Access denied — this account does not belong to you");

        // ── Validate amount ───────────────────────────────────────────────
        BigDecimal requestedAmount = req.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal outstanding     = loan.getOutstandingBalance().setScale(SCALE, RoundingMode.HALF_UP);

        boolean isResidualClearance = outstanding.compareTo(MIN_PAYMENT) < 0
                && requestedAmount.compareTo(outstanding) >= 0;

        if (!isResidualClearance && requestedAmount.compareTo(MIN_PAYMENT) < 0)
            throw new BadRequestException(
                "Minimum repayment is FJD 1.00. Outstanding balance: " + outstanding);

        // Cap at outstanding — customer cannot overpay
        BigDecimal amountPaid = requestedAmount.min(outstanding);

        // ── Sufficient funds check ────────────────────────────────────────
        if (source.getBalance().compareTo(amountPaid) < 0)
            throw new InsufficientFundsException(String.format(
                "Insufficient funds. Available: FJD %.2f, Repayment: FJD %.2f",
                source.getBalance(), amountPaid));

        // ── Principal / interest split ────────────────────────────────────
        BigDecimal monthlyRate     = loan.getInterestRate()
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal interestDue     = outstanding.multiply(monthlyRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal interestComp    = amountPaid.min(interestDue);
        BigDecimal principalComp   = amountPaid.subtract(interestComp)
                .max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal newBalance      = outstanding.subtract(principalComp)
                .max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);

        boolean paidOff = newBalance.compareTo(BigDecimal.ZERO) == 0;

        // ── Reference ─────────────────────────────────────────────────────
        String reference = "REP-" + LocalDate.now().getYear()
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // ── Debit customer account ────────────────────────────────────────
        source.setBalance(source.getBalance().subtract(amountPaid));
        accountRepository.saveAndFlush(source);

        // ── Credit BANK_INTERNAL_OPERATIONS ──────────────────────────────
        //
        // FIX: repayments go back into the bank's funding account.
        // This is the reverse of the approval flow where principal was debited.
        Account bankAccount = accountRepository.findByAccountNumber(bankInternalAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank internal account not found: " + bankInternalAccountNumber));

        bankAccount.setBalance(bankAccount.getBalance().add(amountPaid));
        accountRepository.saveAndFlush(bankAccount);

        // ── Transaction record (visible in admin account history) ─────────
        transactionRepository.save(Transaction.builder()
                .referenceNumber(reference)
                .transactionType(TransactionType.LOAN_REPAYMENT)
                .amount(amountPaid)
                .description("Loan repayment — " + loan.getLoanType()
                        + " [" + loan.getLoanNumber() + "] from " + source.getAccountNumber()
                        + (paidOff ? " — LOAN FULLY REPAID" : ""))
                .sourceAccount(source)
                .destinationAccount(bankAccount)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(source.getBalance())
                .transactionDate(LocalDateTime.now())
                .build());

        // ── Persist repayment record ──────────────────────────────────────
        LoanRepayment repayment = LoanRepayment.builder()
                .reference(reference)
                .loan(loan)
                .sourceAccount(source)
                .amountPaid(amountPaid)
                .principalComponent(principalComp)
                .interestComponent(interestComp)
                .balanceAfter(newBalance)
                .paymentDate(LocalDate.now())
                .build();

        repaymentRepository.save(repayment);

        // ── Update loan balance and status ────────────────────────────────
        loan.setOutstandingBalance(newBalance);
        if (paidOff) {
            // FIX: use CLOSED as the terminal state (ACTIVE → CLOSED per RBF lifecycle)
            // PAID_OFF is kept in the enum for backward compat with existing data
            loan.setStatus(LoanStatus.CLOSED);
            log.info("Loan fully repaid and CLOSED: loanNumber={} user={}",
                    loan.getLoanNumber(), userEmail);
        }
        loanRepository.save(loan);

        log.info("Repayment processed: ref={} loan={} paid={} principal={} interest={} " +
                 "newBalance={} bankBalance={}",
                reference, loan.getLoanNumber(), amountPaid,
                principalComp, interestComp, newBalance, bankAccount.getBalance());

        return toResponse(repayment, paidOff);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns repayment history data.
     * @param userEmail the email of the authenticated user.
     * @param loanId the unique identifier of the target record.
     * @return the matching results.
     */
    public List<LoanRepaymentResponse> getRepaymentHistory(String userEmail, Long loanId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if (!loan.getUser().getId().equals(user.getId()))
            throw new UnauthorizedException("Access denied");

        return repaymentRepository.findByLoanWithAccount(loan)
                .stream()
                .map(r -> toResponse(r, false))
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private LoanRepaymentResponse toResponse(LoanRepayment r, boolean paidOff) {
        Loan    loan   = r.getLoan();
        Account source = r.getSourceAccount();
        return LoanRepaymentResponse.builder()
                .id(r.getId())
                .reference(r.getReference())
                .loanNumber(loan   != null ? loan.getLoanNumber()             : null)
                .loanType  (loan   != null ? loan.getLoanType()               : null)
                .sourceAccountNumber(source != null ? source.getAccountNumber() : null)
                .amountPaid        (r.getAmountPaid())
                .principalComponent(r.getPrincipalComponent())
                .interestComponent (r.getInterestComponent())
                .balanceAfter      (r.getBalanceAfter())
                .loanPaidOff       (paidOff)
                .paymentDate       (r.getPaymentDate())
                .build();
    }
}
