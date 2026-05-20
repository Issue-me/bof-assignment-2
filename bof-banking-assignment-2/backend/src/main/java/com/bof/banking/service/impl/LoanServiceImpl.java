// package com.bof.banking.service.impl;

// import com.bof.banking.dto.loan.LoanRequest;
// import com.bof.banking.dto.loan.LoanResponse;
// import com.bof.banking.mapper.LoanMapper;
// import com.bof.banking.model.Account;
// import com.bof.banking.model.Loan;
// import com.bof.banking.model.User;
// import com.bof.banking.model.enums.LoanStatus;
// import com.bof.banking.repository.AccountRepository;
// import com.bof.banking.repository.LoanRepository;
// import com.bof.banking.repository.UserRepository;
// import com.bof.banking.service.LoanService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.math.BigDecimal;
// import java.math.MathContext;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.List;
// import java.util.UUID;
// import java.util.stream.Collectors;

// /**
//  * Loan service implementing RBF-compliant application processing.
//  *
//  * <h3>Responsible Lending Rules (Reserve Bank of Fiji)</h3>
//  * <ul>
//  *   <li>Maximum Debt Service Ratio (DSR): 40% of gross monthly income</li>
//  *   <li>Minimum income to qualify: FJD 500/month</li>
//  *   <li>Auto-reject DSR &gt; 60% (indicates severe over-indebtedness)</li>
//  * </ul>
//  *
//  * <h3>Interest Rates (per RBF market rates)</h3>
//  * <ul>
//  *   <li>Personal Loan:  8.50% p.a.</li>
//  *   <li>Home Loan:      6.50% p.a.</li>
//  *   <li>Vehicle Loan:   7.50% p.a.</li>
//  *   <li>Business Loan:  9.00% p.a.</li>
//  * </ul>
//  *
//  * <h3>Fixes applied</h3>
//  * <ul>
//  *   <li>Replaced inline {@code toResponse()} with the shared {@link LoanMapper}
//  *       — eliminates duplicate mapping logic and the missing-field bugs.</li>
//  *   <li>{@code approveLoan()} no longer sets a non-existent APPROVED status;
//  *       it transitions directly PENDING → ACTIVE (matching the enum).</li>
//  *   <li>All stream collectors use the mapper so admin list responses now include
//  *       customerFullName, customerId, disbursementAccountNumber, etc.</li>
//  * </ul>
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class LoanServiceImpl implements LoanService {

//     // ── RBF Responsible Lending Constants ──────────────────────────────
//     private static final BigDecimal MAX_DSR            = new BigDecimal("0.40"); // 40 %
//     private static final BigDecimal AUTO_REJECT_DSR    = new BigDecimal("0.60"); // 60 %
//     private static final BigDecimal MIN_MONTHLY_INCOME = new BigDecimal("500.00");
//     private static final int        SCALE              = 2;

//     private final LoanRepository    loanRepository;
//     private final UserRepository    userRepository;
//     private final AccountRepository accountRepository;
//     // FIX: inject shared mapper instead of duplicating mapping logic inline
//     private final LoanMapper        loanMapper;

//     // ── Interest rates by loan type ─────────────────────────────────────
//     private BigDecimal getRate(String loanType) {
//         return switch (loanType) {
//             case "Home Loan"     -> new BigDecimal("0.065");
//             case "Vehicle Loan"  -> new BigDecimal("0.075");
//             case "Business Loan" -> new BigDecimal("0.090");
//             default              -> new BigDecimal("0.085"); // Personal Loan
//         };
//     }

//     // ── Monthly repayment (standard amortisation formula) ───────────────
//     // M = P × r(1+r)^n / ((1+r)^n − 1)
//     private BigDecimal calcMonthlyPayment(BigDecimal principal,
//                                           BigDecimal annualRate,
//                                           int termMonths) {
//         BigDecimal r        = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
//         BigDecimal onePlusR = BigDecimal.ONE.add(r);
//         BigDecimal power    = onePlusR.pow(termMonths, MathContext.DECIMAL64);
//         BigDecimal numerator   = principal.multiply(r).multiply(power);
//         BigDecimal denominator = power.subtract(BigDecimal.ONE);
//         return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
//     }

//     // ── DSR calculation ─────────────────────────────────────────────────
//     private BigDecimal calcDsr(BigDecimal monthlyPayment,
//                                 BigDecimal existingRepayments,
//                                 BigDecimal monthlyIncome) {
//         BigDecimal totalDebt = monthlyPayment
//                 .add(existingRepayments != null ? existingRepayments : BigDecimal.ZERO);
//         return totalDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
//     }

//     // ──────────────────────────────────────────────────────────────────────

//     @Override
//     @Transactional
//     public LoanResponse apply(String userEmail, LoanRequest req) {
//         User user = findUser(userEmail);

//         // Validate disbursement account belongs to this user
//         Account disbAccount = accountRepository.findById(req.getDisbursementAccountId())
//                 .orElseThrow(() -> new RuntimeException("Disbursement account not found"));
//         if (!disbAccount.getUser().getId().equals(user.getId())) {
//             throw new RuntimeException("Account does not belong to this user");
//         }

//         BigDecimal rate        = getRate(req.getLoanType());
//         BigDecimal monthlyPmt  = calcMonthlyPayment(req.getAmount(), rate, req.getTermMonths());
//         BigDecimal totalIncome = req.getMonthlyIncome()
//                 .add(req.getOtherIncome() != null ? req.getOtherIncome() : BigDecimal.ZERO);
//         BigDecimal dsr         = calcDsr(monthlyPmt, req.getExistingMonthlyRepayments(), totalIncome);

//         // ── Auto-reject conditions ──────────────────────────────────────
//         String autoRejectReason = null;
//         if (totalIncome.compareTo(MIN_MONTHLY_INCOME) < 0) {
//             autoRejectReason = "Monthly income below minimum threshold of FJD 500.";
//         } else if (dsr.compareTo(AUTO_REJECT_DSR) > 0) {
//             autoRejectReason = String.format(
//                 "Debt Service Ratio of %.1f%% exceeds the maximum allowed 60%%. " +
//                 "Please reduce the loan amount or extend the term.",
//                 dsr.multiply(BigDecimal.valueOf(100)).doubleValue()
//             );
//         }

//         LoanStatus initialStatus = autoRejectReason != null ? LoanStatus.REJECTED : LoanStatus.PENDING;

//         String loanNumber = "LOAN-" + LocalDate.now().getYear()
//                 + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

//         Loan loan = Loan.builder()
//                 .loanNumber(loanNumber)
//                 .loanType(req.getLoanType())
//                 .principalAmount(req.getAmount())
//                 .outstandingBalance(req.getAmount())
//                 .interestRate(rate)
//                 .termMonths(req.getTermMonths())
//                 .monthlyPayment(monthlyPmt)
//                 .status(initialStatus)
//                 .user(user)
//                 .disbursementAccount(disbAccount)
//                 .applicationDate(LocalDate.now())
//                 .rejectionReason(autoRejectReason)
//                 .build();

//         loan = loanRepository.save(loan);

//         log.info("Loan application created: loanNumber={} user={} type={} amount={} dsr={}% status={}",
//                 loanNumber, userEmail, req.getLoanType(), req.getAmount(),
//                 dsr.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
//                 initialStatus);

//         // FIX: use shared mapper (overload with DSR — only available at application time)
//         return loanMapper.toResponse(loan, dsr);
//     }

//     @Override
//     @Transactional(readOnly = true)
//     public List<LoanResponse> getMyLoans(String userEmail) {
//         User user = findUser(userEmail);
//         return loanRepository.findByUserOrderByApplicationDateDesc(user)
//                 .stream()
//                 .map(loanMapper::toResponse)
//                 .collect(Collectors.toList());
//     }

//     @Override
//     @Transactional(readOnly = true)
//     public LoanResponse getLoanById(String userEmail, Long loanId) {
//         User user = findUser(userEmail);
//         Loan loan = loanRepository.findById(loanId)
//                 .orElseThrow(() -> new RuntimeException("Loan not found"));
//         if (!loan.getUser().getId().equals(user.getId())) {
//             throw new RuntimeException("Access denied");
//         }
//         return loanMapper.toResponse(loan);
//     }

//     @Override
//     @Transactional
//     public LoanResponse approveLoan(Long loanId) {
//         Loan loan = loanRepository.findById(loanId)
//                 .orElseThrow(() -> new RuntimeException("Loan not found"));
//         if (loan.getStatus() != LoanStatus.PENDING) {
//             throw new RuntimeException("Only PENDING loans can be approved");
//         }

//         // FIX: transition directly to ACTIVE (APPROVED status removed from enum —
//         // Bank of Fiji workflow disburses funds immediately on approval)
//         loan.setStatus(LoanStatus.ACTIVE);
//         loan.setApprovalDate(LocalDate.now());
//         loan.setStartDate(LocalDate.now());
//         loan.setEndDate(LocalDate.now().plusMonths(loan.getTermMonths()));

//         // Disburse funds to disbursement account
//         Account acct = loan.getDisbursementAccount();
//         acct.setBalance(acct.getBalance().add(loan.getPrincipalAmount()));
//         accountRepository.save(acct);

//         loan = loanRepository.save(loan);
//         log.info("Loan approved: loanNumber={} disbursed={} to account={}",
//                 loan.getLoanNumber(), loan.getPrincipalAmount(), acct.getAccountNumber());
//         return loanMapper.toResponse(loan);
//     }

//     @Override
//     @Transactional
//     public LoanResponse rejectLoan(Long loanId, String reason) {
//         Loan loan = loanRepository.findById(loanId)
//                 .orElseThrow(() -> new RuntimeException("Loan not found"));
//         if (loan.getStatus() != LoanStatus.PENDING) {
//             throw new RuntimeException("Only PENDING loans can be rejected");
//         }
//         loan.setStatus(LoanStatus.REJECTED);
//         loan.setRejectionReason(reason);
//         loan = loanRepository.save(loan);
//         log.info("Loan rejected: loanNumber={} reason={}", loan.getLoanNumber(), reason);
//         return loanMapper.toResponse(loan);
//     }

//     @Override
//     @Transactional(readOnly = true)
//     public List<LoanResponse> getAllLoans() {
//         return loanRepository.findAllByOrderByApplicationDateDesc()
//                 .stream()
//                 .map(loanMapper::toResponse)
//                 .collect(Collectors.toList());
//     }

//     @Override
//     @Transactional(readOnly = true)
//     public List<LoanResponse> getPendingLoans() {
//         return loanRepository.findByStatusOrderByApplicationDateAsc(LoanStatus.PENDING)
//                 .stream()
//                 .map(loanMapper::toResponse)
//                 .collect(Collectors.toList());
//     }

//     // ── Private helpers ────────────────────────────────────────────────

//     private User findUser(String email) {
//         return userRepository.findByEmail(email)
//                 .orElseThrow(() -> new RuntimeException("User not found: " + email));
//     }
// }

package com.bof.banking.service.impl;

import com.bof.banking.dto.loan.LoanRequest;
import com.bof.banking.dto.loan.LoanResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.LoanMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.Loan;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.LoanStatus;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.LoanRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bof.banking.model.LoanRate;
import com.bof.banking.repository.LoanRateRepository;

import java.util.Map;

/**
 * Loan service implementing RBF-compliant application processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final BigDecimal MAX_DSR            = new BigDecimal("0.40");
    private static final BigDecimal AUTO_REJECT_DSR    = new BigDecimal("0.60");
    private static final BigDecimal MIN_MONTHLY_INCOME = new BigDecimal("500.00");
    private static final int        SCALE              = 2;

    @Value("${app.bank.internal-account-number:BOF90000001}")
    private String bankInternalAccountNumber;

    private final LoanRepository        loanRepository;
    private final UserRepository        userRepository;
    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final LoanMapper            loanMapper;
    private final LoanRateRepository    loanRateRepository;

    // Static fallback rates used only when no DB row exists yet
    private static final Map<String, BigDecimal> FALLBACK_RATES = Map.of(
        "Personal Loan",  new BigDecimal("0.085000"),
        "Home Loan",      new BigDecimal("0.065000"),
        "Vehicle Loan",   new BigDecimal("0.075000"),
        "Business Loan",  new BigDecimal("0.090000")
    );

    /**
     * Returns the current configured rate for a loan type.
     * Reads from loan_rates table — falls back to hardcoded defaults
     * if no row has been set yet.
     */
    private BigDecimal getDefaultRate(String loanType) {
        return loanRateRepository.findByLoanType(loanType)
                .map(LoanRate::getAnnualRate)
                .orElseGet(() -> FALLBACK_RATES.getOrDefault(
                        loanType, new BigDecimal("0.085000")));
    }


    private BigDecimal calcMonthlyPayment(BigDecimal principal,
                                           BigDecimal annualRate,
                                           int termMonths) {
        BigDecimal r        = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal power    = onePlusR.pow(termMonths, MathContext.DECIMAL64);
        BigDecimal num      = principal.multiply(r).multiply(power);
        BigDecimal den      = power.subtract(BigDecimal.ONE);
        return num.divide(den, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calcDsr(BigDecimal monthlyPayment,
                                BigDecimal existingRepayments,
                                BigDecimal monthlyIncome) {
        BigDecimal totalDebt = monthlyPayment
                .add(existingRepayments != null ? existingRepayments : BigDecimal.ZERO);
        return totalDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
    }

    // ── apply ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles apply.
     * @param userEmail the email of the authenticated user.
     * @param req the request payload.
     * @return the result of the operation.
     */
    public LoanResponse apply(String userEmail, LoanRequest req) {
        User user = findUser(userEmail);

        Account disbAccount = accountRepository.findById(req.getDisbursementAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement account not found"));
        if (!disbAccount.getUser().getId().equals(user.getId()))
            throw new BadRequestException("Account does not belong to this user");

        BigDecimal rate       = getDefaultRate(req.getLoanType());
        BigDecimal monthlyPmt = calcMonthlyPayment(req.getAmount(), rate, req.getTermMonths());
        BigDecimal totalIncome = req.getMonthlyIncome()
                .add(req.getOtherIncome() != null ? req.getOtherIncome() : BigDecimal.ZERO);
        BigDecimal dsr = calcDsr(monthlyPmt, req.getExistingMonthlyRepayments(), totalIncome);

        String autoRejectReason = null;
        if (totalIncome.compareTo(MIN_MONTHLY_INCOME) < 0) {
            autoRejectReason = "Monthly income below minimum threshold of FJD 500.";
        } else if (dsr.compareTo(AUTO_REJECT_DSR) > 0) {
            autoRejectReason = String.format(
                "Debt Service Ratio of %.1f%% exceeds the maximum allowed 60%%. " +
                "Please reduce the loan amount or extend the term.",
                dsr.multiply(BigDecimal.valueOf(100)).doubleValue()
            );
        }

        LoanStatus initialStatus = autoRejectReason != null ? LoanStatus.REJECTED : LoanStatus.PENDING;

        String loanNumber = "LOAN-" + LocalDate.now().getYear()
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Loan loan = Loan.builder()
                .loanNumber(loanNumber)
                .loanType(req.getLoanType())
                .purpose(req.getPurpose())
                .principalAmount(req.getAmount())
                .outstandingBalance(req.getAmount())
                .interestRate(rate)
                .termMonths(req.getTermMonths())
                .monthlyPayment(monthlyPmt)
                .status(initialStatus)
                .user(user)
                .disbursementAccount(disbAccount)
                .employmentType(req.getEmploymentType())
                .monthlyIncome(req.getMonthlyIncome())
                .applicationDate(LocalDate.now())
                .rejectionReason(autoRejectReason)
                .build();

        loan = loanRepository.save(loan);
        log.info("Loan application: loanNumber={} user={} type={} amount={} dsr={}% status={}",
                loanNumber, userEmail, req.getLoanType(), req.getAmount(),
                dsr.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                initialStatus);

        return loanMapper.toResponse(loan, dsr);
    }

    // ── approveLoan ───────────────────────────────────────────────────────
    //
    // FIX: debit BANK_INTERNAL_OPERATIONS and credit the disbursement account,
    // then write a Transaction record so it appears in account history.

    @Override
    @Transactional
    /**
     * Handles approve loan.
     * @param loanId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public LoanResponse approveLoan(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.PENDING)
            throw new BadRequestException("Only PENDING loans can be approved. Current status: " + loan.getStatus());

        Account disbAccount = loan.getDisbursementAccount();
        if (disbAccount == null)
            throw new BadRequestException("Loan has no disbursement account");

        // ── Debit bank internal account ───────────────────────────────────
        Account bankAccount = accountRepository.findByAccountNumber(bankInternalAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank internal account not found: " + bankInternalAccountNumber));

        if (bankAccount.getBalance().compareTo(loan.getPrincipalAmount()) < 0)
            throw new BadRequestException(
                "Bank insufficient funds to disburse FJD " + loan.getPrincipalAmount());

        bankAccount.setBalance(bankAccount.getBalance().subtract(loan.getPrincipalAmount()));
        accountRepository.saveAndFlush(bankAccount);

        // ── Credit disbursement account ───────────────────────────────────
        disbAccount.setBalance(disbAccount.getBalance().add(loan.getPrincipalAmount()));
        accountRepository.saveAndFlush(disbAccount);

        // ── Loan disbursement transaction record ──────────────────────────
        String disbRef = "LOAN-DISB-" + loan.getLoanNumber();
        transactionRepository.save(Transaction.builder()
                .referenceNumber(disbRef)
                .transactionType(TransactionType.LOAN_DISBURSEMENT)
                .amount(loan.getPrincipalAmount())
                .description("Loan disbursement — " + loan.getLoanType()
                        + " [" + loan.getLoanNumber() + "] to " + disbAccount.getAccountNumber())
                .sourceAccount(bankAccount)
                .destinationAccount(disbAccount)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(disbAccount.getBalance())
                .transactionDate(LocalDateTime.now())
                .build());

        // ── Update loan ───────────────────────────────────────────────────
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setApprovalDate(LocalDate.now());
        loan.setStartDate(LocalDate.now());
        loan.setEndDate(LocalDate.now().plusMonths(loan.getTermMonths()));
        loan = loanRepository.save(loan);

        log.info("Loan approved: loanNumber={} disbursed FJD {} to account={} bank balance={}",
                loan.getLoanNumber(), loan.getPrincipalAmount(),
                disbAccount.getAccountNumber(), bankAccount.getBalance());

        return loanMapper.toResponse(loan);
    }

    // ── updateLoanInterestRate ────────────────────────────────────────────
    //
    // Allows admin to change the interest rate on an individual ACTIVE loan.
    // Recalculates monthly payment with the new rate and remaining term.

    @Override
    @Transactional
    /**
     * Updates loan interest rate values.
     * @param loanId the unique identifier of the target record.
     * @param newAnnualRate the monetary value used by this operation.
     * @return the result of the operation.
     */
    public LoanResponse updateLoanInterestRate(Long loanId, BigDecimal newAnnualRate) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BadRequestException("Interest rate can only be changed on ACTIVE loans");

        if (newAnnualRate.compareTo(BigDecimal.ZERO) <= 0 ||
                newAnnualRate.compareTo(new BigDecimal("1.0")) > 0)
            throw new BadRequestException("Annual rate must be between 0% and 100%");

        BigDecimal oldRate = loan.getInterestRate();

        // Estimate remaining term (months from today to endDate, min 1)
        int remainingMonths = 1;
        if (loan.getEndDate() != null) {
            long months = java.time.temporal.ChronoUnit.MONTHS.between(LocalDate.now(), loan.getEndDate());
            remainingMonths = (int) Math.max(1, months);
        }

        BigDecimal newMonthlyPayment = calcMonthlyPayment(
                loan.getOutstandingBalance(), newAnnualRate, remainingMonths);

        loan.setInterestRate(newAnnualRate);
        loan.setMonthlyPayment(newMonthlyPayment);
        loan = loanRepository.save(loan);

        log.info("Loan interest rate updated: loanNumber={} oldRate={}% newRate={}% newMonthly={}",
                loan.getLoanNumber(),
                oldRate.multiply(BigDecimal.valueOf(100)).toPlainString(),
                newAnnualRate.multiply(BigDecimal.valueOf(100)).toPlainString(),
                newMonthlyPayment);

        return loanMapper.toResponse(loan);
    }

    // ── rejectLoan ────────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles reject loan.
     * @param loanId the unique identifier of the target record.
     * @param reason the reason.
     * @return the result of the operation.
     */
    public LoanResponse rejectLoan(Long loanId, String reason) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.PENDING)
            throw new BadRequestException("Only PENDING loans can be rejected");
        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        loan = loanRepository.save(loan);
        log.info("Loan rejected: loanNumber={} reason={}", loan.getLoanNumber(), reason);
        return loanMapper.toResponse(loan);
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns loan by id data.
     * @param userEmail the email of the authenticated user.
     * @param loanId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public LoanResponse getLoanById(String userEmail, Long loanId) {
        User user = findUser(userEmail);
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        if (!loan.getUser().getId().equals(user.getId()))
            throw new BadRequestException("Access denied");
        return loanMapper.toResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns my loans data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<LoanResponse> getMyLoans(String userEmail) {
        User user = findUser(userEmail);
        return loanRepository.findByUserOrderByApplicationDateDesc(user)
                .stream().map(loanMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns all loans data.
     * @return the matching results.
     */
    public List<LoanResponse> getAllLoans() {
        return loanRepository.findAllByOrderByApplicationDateDesc()
                .stream().map(loanMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns pending loans data.
     * @return the matching results.
     */
    public List<LoanResponse> getPendingLoans() {
        return loanRepository.findByStatusOrderByApplicationDateAsc(LoanStatus.PENDING)
                .stream().map(loanMapper::toResponse).collect(Collectors.toList());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
