package com.bof.banking.service.impl;

import com.bof.banking.dto.tax.FrcsInterestSummaryReport;
import com.bof.banking.dto.tax.TaxReportResponse;
import com.bof.banking.dto.tax.TaxSubmitResponse;
import com.bof.banking.model.Account;
import com.bof.banking.model.InterestRate;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.UserInterestSummary;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.model.enums.RiwtExemptionStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.InterestRateRepository;
import com.bof.banking.repository.InterestSummaryRepository;
import com.bof.banking.repository.RiwtExemptionRepository;
import com.bof.banking.repository.SavingsInterestTransactionRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TaxServiceImpl
 *
 * NRWHT vs RIWT historical accuracy rule:
 *
 *   When a customer (e.g. Adrian) had no TIN → NRWHT was charged.
 *   After a teller registers her TIN via UserServiceImpl.updateUser():
 *     - NrwhtRefundService credits the NRWHT amount back to her account
 *     - A DEPOSIT transaction "NRWHT Refund…" is written to the transactions table
 *     - Future interest uses RIWT
 *
 *   The persisted UserInterestSummary stores the historically accurate split:
 *     nrwhtWithheld = what was charged before TIN, riwtWithheld = what was charged after.
 *
 *   We ALWAYS read nrwht/riwt from the persisted summary when it exists.
 *   We NEVER recalculate from the current user profile — that would overwrite
 *   historical NRWHT with RIWT for mid-year TIN registrations.
 *
 * NRWHT refund shown when:
 *   1. persisted nrwhtWithheld > 0
 *   2. user is now resident with TIN
 *   3. A "NRWHT Refund" DEPOSIT transaction exists (written by NrwhtRefundService)
 *
 * If TIN was set directly in DataSeeder (bypassing UserServiceImpl), condition 3
 * will be false → nrwhtRefunded = false. This is intentional and correct: no
 * actual refund transaction occurred in that case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxServiceImpl implements TaxService {

    private final UserRepository             userRepository;
    private final AccountRepository          accountRepository;
    private final TransactionRepository      transactionRepository;
    private final InterestRateRepository     interestRateRepository;
    private final RiwtExemptionRepository    riwtExemptionRepository;
    private final InterestSummaryRepository  interestSummaryRepository;
    private final InterestSummaryCalculator  interestSummaryCalculator;
    private final SavingsInterestTransactionRepository interestTaxRepository; 

    // ── 1. getReport ──────────────────────────────────────────────────────────

    @Override
    /**
     * Returns report data.
     * @param userEmail the email of the authenticated user.
     * @param year the date or time value used by this operation.
     * @return the result of the operation.
     */
    public TaxReportResponse getReport(String userEmail, int year) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear   = LocalDate.of(year, 12, 31);
        LocalDate today       = LocalDate.now();

        LocalDate rateAsOf = endOfYear.isAfter(today) ? today : endOfYear;
        Optional<InterestRate> rateOpt =
            interestRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(rateAsOf);

        String interestRateDisplay = rateOpt
            .map(r -> r.getAnnualRate()
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .toPlainString() + "% p.a.")
            .orElse("Not set");

        boolean resident     = user.isResident();
        boolean riwtExempt   = riwtExemptionRepository
            .existsByUserAndTaxYearAndStatus(user, year, RiwtExemptionStatus.APPROVED);
        boolean riwtRejected = riwtExemptionRepository
            .existsByUserAndTaxYearAndStatus(user, year, RiwtExemptionStatus.REJECTED);

        LocalDateTime from = startOfYear.atStartOfDay();
        LocalDateTime to   = endOfYear.atTime(23, 59, 59);

        BigDecimal grossInterest = transactionRepository
            .sumAmountByUserAndTypeAndAccountTypeAndDateRange(
                user, TransactionType.INTEREST, AccountType.SAVINGS, from, to)
            .orElse(BigDecimal.ZERO);

        Optional<UserInterestSummary> summaryOpt =
                interestSummaryRepository.findByUserAndTaxYear(user, year);

        BigDecimal nrwhtWithheld;
        BigDecimal riwtWithheld;

        if (summaryOpt.isPresent()) {
            // USE PERSISTED VALUES — historically accurate, never overwrite.
            UserInterestSummary summary = summaryOpt.get();
            nrwhtWithheld = coalesce(summary.getNrwhtWithheld());
            riwtWithheld  = coalesce(summary.getRiwtWithheld());
        } else {
            // No persisted summary yet — fall back to live calculation from
            // current profile. This path only runs for brand-new users who have
            // never had interest calculated or submitted a return.
            boolean hasTin = user.getTinNumber() != null && !user.getTinNumber().isBlank();
            BigDecimal rate10 = new BigDecimal("0.10");

            if (user.isSeniorCitizen() || riwtExempt) {
                nrwhtWithheld = BigDecimal.ZERO;
                riwtWithheld  = BigDecimal.ZERO;
            } else if (!resident || !hasTin) {
                // Non-resident OR resident without TIN → NRWHT
                nrwhtWithheld = grossInterest.multiply(rate10).setScale(2, RoundingMode.HALF_UP);
                riwtWithheld  = BigDecimal.ZERO;
            } else {
                // Resident with TIN → RIWT
                nrwhtWithheld = BigDecimal.ZERO;
                riwtWithheld  = grossInterest.multiply(rate10).setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal netInterest = grossInterest
                .subtract(riwtWithheld)
                .subtract(nrwhtWithheld)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Step 2: detect NRWHT refund ──────────────────────────────────────
        // A refund is shown when:
        //   (a) NRWHT was actually charged (nrwhtWithheld > 0)
        //   (b) The user is now a resident with a TIN (refund eligibility)
        //   (c) A NRWHT Refund DEPOSIT transaction exists in the DB
        //       (written by NrwhtRefundService — not present if TIN was set
        //        directly via DataSeeder bypassing UserServiceImpl)
        boolean nrwhtRefunded  = false;
        String  nrwhtRefundRef = null;

        if (nrwhtWithheld.compareTo(BigDecimal.ZERO) > 0) {
            if (summaryOpt.isPresent() && summaryOpt.get().isNrwhtRefunded()) {
                nrwhtRefunded = true;
                nrwhtRefundRef = summaryOpt.get().getNrwhtRefundReference();
            } else if (user.isResident() && user.hasTin()) {
                List<Account> savingsAccounts = accountRepository.findByUser(user).stream()
                        .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
                        .collect(Collectors.toList());
                for (Account sa : savingsAccounts) {
                    List<Transaction> refundTxns =
                            transactionRepository.findNrwhtRefundsByAccountAndYear(sa, year);
                    if (!refundTxns.isEmpty()) {
                        nrwhtRefunded  = true;
                        nrwhtRefundRef = refundTxns.get(0).getReferenceNumber();
                        break;
                    }
                }
            }
        }

        // ── Step 3: income & tax figures ─────────────────────────────────────
        BigDecimal totalCredits  = transactionRepository.sumCreditsByUserAndDateRange(user, from, to).orElse(BigDecimal.ZERO);
        BigDecimal totalDebits   = transactionRepository.sumDebitsByUserAndDateRange(user, from, to).orElse(BigDecimal.ZERO);
        long       txCount       = transactionRepository.countByUserAndDateRange(user, from, to);
        BigDecimal bankFees      = transactionRepository.sumFeesByUserAndDateRange(user, from, to).orElse(BigDecimal.ZERO);

        BigDecimal grossIncome   = totalCredits;
        BigDecimal fnpfEmployee  = grossIncome.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fnpfEmployer  = grossIncome.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableIncome = grossIncome.subtract(fnpfEmployee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payeOwed      = resident ? calculatePaye(taxableIncome) : BigDecimal.ZERO;
        BigDecimal vatOnFees     = bankFees.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTaxOwed  = payeOwed.add(riwtWithheld).add(nrwhtWithheld).add(vatOnFees)
                                           .setScale(2, RoundingMode.HALF_UP);

        // ── Step 4: submission status ─────────────────────────────────────────
        boolean customerSubmitted = summaryOpt.map(UserInterestSummary::isCustomerSubmitted).orElse(false);
        boolean frcsSubmitted     = summaryOpt.map(UserInterestSummary::isSubmittedToFrcs).orElse(false);
        String savedReference     = summaryOpt.map(UserInterestSummary::getFrcsReference).orElse(null);

        String status;
        if (frcsSubmitted)          status = "SUBMITTED";
        else if (customerSubmitted) status = "PENDING_FRCS";
        else                        status = "DRAFT";

        List<TaxReportResponse.MonthlyBreakdown> monthly =
            buildMonthlyBreakdown(user, year, riwtExempt, user.isSeniorCitizen());

        return TaxReportResponse.builder()
                .fullName(user.getFirstName() + " " + user.getLastName())
                .customerId(user.getCustomerId())
                .tinNumber(user.hasTin() ? user.getTinNumber() : "Not Provided")
                .taxYear(year)
                .resident(resident)
                .seniorCitizen(user.isSeniorCitizen())
                .interestRate(interestRateDisplay)
                .riwtExempt(riwtExempt)
                .riwtRejected(riwtRejected)
                .interestEarned(grossInterest)
                .riwtWithheld(riwtWithheld)
                .nrwhtWithheld(nrwhtWithheld)
                .nrwhtRefunded(nrwhtRefunded)
                .nrwhtRefundReference(nrwhtRefundRef)
                .netInterestPaid(netInterest)
                .grossIncome(grossIncome)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .taxableIncome(taxableIncome)
                .transactionCount(txCount)
                .payeOwed(payeOwed)
                .vatOnFees(vatOnFees)
                .bankFeesCharged(bankFees)
                .fnpfEmployee(fnpfEmployee)
                .fnpfEmployer(fnpfEmployer)
                .totalTaxOwed(totalTaxOwed)
                .status(status)
                .submittedToFrcs(frcsSubmitted)
                .frcsReference(savedReference)
                .monthlyBreakdown(monthly)
                .build();
    }

    // ── 2. submitReturn ───────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles submit return.
     * @param userEmail the email of the authenticated user.
     * @param year the date or time value used by this operation.
     * @return the result of the operation.
     */
    public TaxSubmitResponse submitReturn(String userEmail, int year) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // Only create a summary if none exists — NEVER overwrite historical NRWHT data
        boolean summaryExists = interestSummaryRepository
                .findByUserAndTaxYear(user, year).isPresent();
        if (!summaryExists) {
            interestSummaryCalculator.calculateAndSave(user, year);
        }

        String reference = "FRCS-" + year + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        interestSummaryRepository.findByUserAndTaxYear(user, year).ifPresent(summary -> {
            summary.setCustomerSubmitted(true);
            summary.setCustomerSubmittedDate(LocalDate.now());
            summary.setFrcsReference(reference);
            interestSummaryRepository.save(summary);
        });

        log.info("Tax return submitted: user={} year={} ref={}", userEmail, year, reference);

        return TaxSubmitResponse.builder()
                .referenceNumber(reference)
                .message("Your " + year + " tax return has been received by Bank of Fiji. "
                    + "Reference: " + reference + ". The bank will forward this to FRCS.")
                .submittedAt(LocalDateTime.now())
                .build();
    }

    // ── 3. generateFrcsInterestSummary ────────────────────────────────────────

    @Override
    /**
     * Handles generate frcs interest summary.
     * @param year the date or time value used by this operation.
     * @return the result of the operation.
     */
    public FrcsInterestSummaryReport generateFrcsInterestSummary(int year) {
        List<User> allUsers = userRepository.findAll();

        List<FrcsInterestSummaryReport.UserInterestRecord> records = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalNrwht = BigDecimal.ZERO;
        BigDecimal totalRiwt  = BigDecimal.ZERO;
        BigDecimal totalNrwhtRefunded = BigDecimal.ZERO;

        for (User user : allUsers) {
            UserInterestSummary summary = interestSummaryRepository
                    .findByUserAndTaxYear(user, year)
                    .orElseGet(() -> interestSummaryCalculator.calculateTransient(user, year));

            if (summary.getGrossInterestEarned().compareTo(BigDecimal.ZERO) == 0) continue;

            // Detect refund: nrwhtWithheld > 0 + user now has TIN + refund tx exists
            boolean recordRefunded  = false;
            String  recordRefundRef = null;

            if (coalesce(summary.getNrwhtWithheld()).compareTo(BigDecimal.ZERO) > 0) {
                if (summary.isNrwhtRefunded()) {
                    recordRefunded = true;
                    recordRefundRef = summary.getNrwhtRefundReference();
                } else if (user.isResident() && user.hasTin()) {
                    List<Account> accts = accountRepository.findByUser(user).stream()
                            .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
                            .collect(Collectors.toList());
                    for (Account acct : accts) {
                        List<Transaction> refs =
                                transactionRepository.findNrwhtRefundsByAccountAndYear(acct, year);
                        if (!refs.isEmpty()) {
                            recordRefunded  = true;
                            recordRefundRef = refs.get(0).getReferenceNumber();
                            break;
                        }
                    }
                }
            }

            records.add(FrcsInterestSummaryReport.UserInterestRecord.builder()
                    .customerId(user.getCustomerId())
                    .fullName(user.getFullName())
                    .tinNumber(user.hasTin() ? user.getTinNumber() : "Not Provided")
                    .resident(user.isResident())
                    .seniorCitizen(user.isSeniorCitizen())
                    .grossInterestEarned(summary.getGrossInterestEarned())
                    .nrwhtWithheld(coalesce(summary.getNrwhtWithheld()))
                    .riwtWithheld(coalesce(summary.getRiwtWithheld()))
                    .netInterestPaid(summary.getNetInterestPaid())
                    .exemptionReason(summary.getExemptionReason())
                    .customerSubmitted(summary.isCustomerSubmitted())
                    .customerSubmittedDate(summary.getCustomerSubmittedDate())
                    .nrwhtRefunded(recordRefunded)
                    .nrwhtRefundReference(recordRefundRef)
                    .build());

            totalGross = totalGross.add(summary.getGrossInterestEarned());
            totalNrwht = totalNrwht.add(coalesce(summary.getNrwhtWithheld()));
            totalRiwt  = totalRiwt.add(coalesce(summary.getRiwtWithheld()));
            if (recordRefunded) {
                totalNrwhtRefunded = totalNrwhtRefunded.add(coalesce(summary.getNrwhtWithheld()));
            }
        }

        BigDecimal totalWithholdingToRemit = totalRiwt
                .add(totalNrwht)
                .subtract(totalNrwhtRefunded)
                .max(BigDecimal.ZERO);

        log.info("FRCS summary: year={} users={} gross={} nrwht={} riwt={} refunded={} remit={}",
                year, records.size(), totalGross, totalNrwht, totalRiwt, totalNrwhtRefunded, totalWithholdingToRemit);

        return FrcsInterestSummaryReport.builder()
                .bankName("Bank of Fiji")
                .bankTin("BOF-TIN-001")
                .taxYear(year)
                .reportGeneratedDate(LocalDate.now())
                .userRecords(records)
                .totalGrossInterestPaid(totalGross)
                .totalNrwhtWithheld(totalNrwht)
                .totalRiwtWithheld(totalRiwt)
                .totalNrwhtRefunded(totalNrwhtRefunded)
                .totalWithholdingToRemit(totalWithholdingToRemit)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal coalesce(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal calculatePaye(BigDecimal taxableIncome) {
        double income = taxableIncome.doubleValue();
        double paye;
        if      (income <= 30_000) paye = 0;
        else if (income <= 50_000) paye = (income - 30_000) * 0.18;
        else                       paye = 20_000 * 0.18 + (income - 50_000) * 0.20;
        return BigDecimal.valueOf(paye).setScale(2, RoundingMode.HALF_UP);
    }

    private List<TaxReportResponse.MonthlyBreakdown> buildMonthlyBreakdown(
            User user, int year, boolean riwtExempt, boolean seniorCitizen) {

        List<TaxReportResponse.MonthlyBreakdown> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDateTime start = LocalDate.of(year, m, 1).atStartOfDay();
            LocalDateTime end   = LocalDate.of(year, m, 1)
                                           .withDayOfMonth(LocalDate.of(year, m, 1).lengthOfMonth())
                                           .atTime(23, 59, 59);

            BigDecimal income = transactionRepository
                .sumCreditsByUserAndDateRange(user, start, end).orElse(BigDecimal.ZERO);
            BigDecimal interest = transactionRepository
                .sumAmountByUserAndTypeAndAccountTypeAndDateRange(
                    user, TransactionType.INTEREST, AccountType.SAVINGS, start, end)
                .orElse(BigDecimal.ZERO);
            BigDecimal taxWithheld = BigDecimal.ZERO;
            if (!seniorCitizen && !riwtExempt && interest.compareTo(BigDecimal.ZERO) > 0) {
                taxWithheld = interest.multiply(new BigDecimal("0.10"))
                                      .setScale(2, RoundingMode.HALF_UP);
            }
            months.add(TaxReportResponse.MonthlyBreakdown.builder()
                    .month(LocalDate.of(year, m, 1).getMonth()
                                   .getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    .income(income)
                    .interest(interest)
                    .taxWithheld(taxWithheld)
                    .build());
        }
        return months;
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns pending nrwht refund amount data.
     * @param userId the unique identifier of the target record.
     * @return an optional value when a matching record exists.
     */
    public Optional<BigDecimal> getPendingNrwhtRefundAmount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        int year = LocalDate.now().getYear();
        List<Account> savingsAccounts = accountRepository.findByUser(user).stream()
                .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
                .collect(Collectors.toList());
        
        // Check if refund conditions are met but no refund transaction exists yet
        boolean eligibleForRefund = user.isResident() 
            && user.hasTin()
            && savingsAccounts.stream().allMatch(a -> 
                transactionRepository.findNrwhtRefundsByAccountAndYear(a, year).isEmpty());
        
        if (!eligibleForRefund) {
            return Optional.empty();
        }
        
        // Calculate pending NRWHT amount (same logic as NrwhtRefundService)
        // BigDecimal totalNrwht = savingsAccounts.stream()
        //         .map(a -> interestTaxRepository.sumRiwtByAccountAndYear(a, year))
        //         .filter(Objects::nonNull)
        //         .reduce(BigDecimal.ZERO, BigDecimal::add)
        //         .setScale(2, RoundingMode.HALF_UP);

        Optional<UserInterestSummary> summaryOpt =
        interestSummaryRepository.findByUserAndTaxYear(user, year);

        if (summaryOpt.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal totalNrwht = coalesce(summaryOpt.get().getNrwhtWithheld());
        
        return totalNrwht.compareTo(BigDecimal.ZERO) > 0 
            ? Optional.of(totalNrwht) 
            : Optional.empty();
    }
}
