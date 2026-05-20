package com.bof.banking.service.impl;

import com.bof.banking.model.Account;
import com.bof.banking.model.User;
import com.bof.banking.model.UserInterestSummary;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.model.enums.RiwtExemptionStatus;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.InterestSummaryRepository;
import com.bof.banking.repository.RiwtExemptionRepository;
import com.bof.banking.repository.SavingsInterestTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * Reads what was ACTUALLY deducted from
 * SavingsInterestTransaction (riwtDeducted column), then classify by
 * comparing what the user's profile was AT THAT TIME — which is already
 * encoded in the transaction records themselves. Since the transactions
 * store the actual amounts, we just need to split them correctly.
 *
 * THE FIXES
 * ─────────────────────────────────────────────────────────────────────────────
 * sumRiwtByAccountAndYear → total actually withheld (regardless of NRWHT/RIWT)
 *
 * To split it into NRWHT vs RIWT:
 *   - If the user currently has no TIN OR is non-resident → ALL withheld = NRWHT
 *   - If the user currently has a TIN AND is resident → ALL withheld = RIWT
 *   - BUT if the user JUST registered a TIN this year (previously no TIN),
 *     we know Jan–(registration month) was NRWHT and after = RIWT.
 *
 * SIMPLEST CORRECT RULE (used here):
 *   Read the seeded/persisted summary first. If one already exists and has
 *   nrwht > 0, PRESERVE it — do not overwrite with a recalculated value.
 *   Only create a new summary if none exists yet.
 *
 * For NEW summaries (no existing record): derive NRWHT vs RIWT from the
 * user's CURRENT profile applied to total actual withholding. This is
 * correct for initial seeding and for users who haven't changed profile.
 *
 * NEVER call calculateAndSave() to overwrite an existing summary that
 * contains NRWHT data. Use updateSummaryAmounts() instead if you need
 * to refresh totals after a refund.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestSummaryCalculator {

    private final AccountRepository                    accountRepository;
    private final SavingsInterestTransactionRepository interestTxRepository;
    private final InterestSummaryRepository            interestSummaryRepository;
    private final RiwtExemptionRepository              riwtExemptionRepository;

    /**
     * Calculate and save (or update) a UserInterestSummary for the given user and year.
     *
     * CRITICAL: If a summary already exists and has nrwhtWithheld > 0, this method
     * PRESERVES the existing nrwht/riwt split. It only updates the gross/net totals
     * from actual SavingsInterestTransaction records. This prevents the overwrite bug
     * where recalculating from current profile replaces historical NRWHT with RIWT.
     *
     * For users with no existing summary, it calculates from scratch using actual
     * transaction data and classifies withholding based on current profile.
     */
    @Transactional
    public void calculateAndSave(User user, int year) {
        // Step 1: gather all active savings accounts
        List<Account> savingsAccounts = accountRepository.findByUser(user).stream()
                .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
                .collect(Collectors.toList());

        // Step 2: sum actual values from SavingsInterestTransaction records
        // These are the REAL amounts credited/deducted — not recalculated estimates
        BigDecimal gross = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumGrossInterestByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActuallyWithheld = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumRiwtByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal net = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumNetInterestByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int txnCount = savingsAccounts.stream()
                .mapToInt(a -> interestTxRepository
                        .findByAccountAndInterestYearOrderByCreditedAtDesc(a, year).size())
                .sum();

        // Step 3: check for existing summary
        Optional<UserInterestSummary> existingOpt =
                interestSummaryRepository.findByUserAndTaxYear(user, year);

        // Step 4: determine NRWHT vs RIWT split
        //
        // KEY RULE: if an existing summary already has nrwht > 0, PRESERVE that split.
        // This protects historical NRWHT data from being overwritten when the user
        // later registers a TIN (which would make the recalculation classify as RIWT).
        BigDecimal nrwht;
        BigDecimal riwt;

        if (existingOpt.isPresent() &&
                existingOpt.get().getNrwhtWithheld() != null &&
                existingOpt.get().getNrwhtWithheld().compareTo(BigDecimal.ZERO) > 0) {

            // PRESERVE existing nrwht/riwt split — do not overwrite
            UserInterestSummary existing = existingOpt.get();
            BigDecimal existingNrwht = existing.getNrwhtWithheld();
            BigDecimal existingRiwt  = existing.getRiwtWithheld();

            // If there's new withholding beyond what was previously recorded as NRWHT,
            // the difference is RIWT (post-TIN-registration months)
            BigDecimal previouslyRecorded = existingNrwht.add(existingRiwt);
            BigDecimal newWithholding = totalActuallyWithheld.subtract(previouslyRecorded);

            if (newWithholding.compareTo(BigDecimal.ZERO) > 0) {
                // New withholding since last summary update — must be RIWT (has TIN now)
                nrwht = existingNrwht;
                riwt  = existingRiwt.add(newWithholding);
                log.info("InterestSummaryCalculator: userId={} year={} — preserving NRWHT={} + new RIWT={}",
                        user.getId(), year, nrwht, newWithholding);
            } else {
                // No new withholding — keep existing split exactly
                nrwht = existingNrwht;
                riwt  = existingRiwt;
                log.info("InterestSummaryCalculator: userId={} year={} — preserving existing split NRWHT={} RIWT={}",
                        user.getId(), year, nrwht, riwt);
            }
        } else {
            // No existing summary (or existing summary has nrwht=0) —
            // classify based on current user profile.
            // This is correct for initial seeding and for users who haven't changed profile.
            boolean isExempt = riwtExemptionRepository.existsByUserAndTaxYearAndStatus(
                    user, year, RiwtExemptionStatus.APPROVED);
            boolean isSenior = user.isSeniorCitizen();

            if (isExempt || isSenior) {
                nrwht = BigDecimal.ZERO;
                riwt  = BigDecimal.ZERO;
            } else if (!user.isResident() || user.getTinNumber() == null || user.getTinNumber().isBlank()) {
                // No TIN or non-resident → all withheld was NRWHT
                nrwht = totalActuallyWithheld;
                riwt  = BigDecimal.ZERO;
            } else {
                // Resident with TIN → all withheld was RIWT
                nrwht = BigDecimal.ZERO;
                riwt  = totalActuallyWithheld;
            }
        }

        // Step 5: determine exemption reason
        String exemptionReason = null;
        if (user.isSeniorCitizen()) {
            exemptionReason = "SENIOR_CITIZEN_EXEMPTION";
        } else if (riwtExemptionRepository.existsByUserAndTaxYearAndStatus(
                user, year, RiwtExemptionStatus.APPROVED)) {
            exemptionReason = "RIWT_EXEMPTION";
        }

        // Step 6: save or update
        if (existingOpt.isPresent()) {
            UserInterestSummary existing = existingOpt.get();
                        boolean preservedRefunded = existing.isNrwhtRefunded();
                        String preservedRefundRef = existing.getNrwhtRefundReference();
            existing.setGrossInterestEarned(gross);
            existing.setNrwhtWithheld(nrwht);
            existing.setRiwtWithheld(riwt);
            existing.setNetInterestPaid(net);
            existing.setInterestTransactionCount(txnCount);
            existing.setExemptionReason(exemptionReason);
                        existing.setNrwhtRefunded(preservedRefunded);
                        existing.setNrwhtRefundReference(preservedRefundRef);
            interestSummaryRepository.save(existing);
            log.info("Interest summary UPDATED: userId={} year={} gross={} nrwht={} riwt={} exempt={}",
                    user.getId(), year, gross, nrwht, riwt, exemptionReason);
        } else {
            interestSummaryRepository.save(UserInterestSummary.builder()
                    .user(user)
                    .taxYear(year)
                    .grossInterestEarned(gross)
                    .nrwhtWithheld(nrwht)
                    .riwtWithheld(riwt)
                    .netInterestPaid(net)
                    .exemptionReason(exemptionReason)
                    .interestTransactionCount(txnCount)
                    .submittedToFrcs(false)
                    .build());
            log.info("Interest summary CREATED: userId={} year={} gross={} nrwht={} riwt={} exempt={}",
                    user.getId(), year, gross, nrwht, riwt, exemptionReason);
        }
    }

    /**
     * Handles mark nrwht refunded.
     * @param user the authenticated user context.
     * @param year the date or time value used by this operation.
     */
    @Transactional
    public void markNrwhtRefunded(User user, int year) {
        interestSummaryRepository.findByUserAndTaxYear(user, year).ifPresent(summary -> {
            // The nrwhtWithheld stays as-is (it WAS deducted — that's a historical fact).
            // The frontend uses nrwht > 0 + user has TIN + isResident to show "REFUNDED".
            // Nothing to change in the summary itself — the refund is a separate Transaction.
            log.info("markNrwhtRefunded: userId={} year={} — nrwht={} remains recorded (refund is separate Transaction)",
                    user.getId(), year, summary.getNrwhtWithheld());
        });
    }

    @Transactional(readOnly = true)
    /**
     * Handles calculate transient.
     * @param user the authenticated user context.
     * @param year the date or time value used by this operation.
     * @return the result of the operation.
     */
    public UserInterestSummary calculateTransient(User user, int year) {

        // Same logic as calculateAndSave BUT without saving

        List<Account> savingsAccounts = accountRepository.findByUser(user).stream()
                .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
                .collect(Collectors.toList());

        BigDecimal gross = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumGrossInterestByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActuallyWithheld = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumRiwtByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal net = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumNetInterestByAccountAndYear(a, year))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int txnCount = savingsAccounts.stream()
                .mapToInt(a -> interestTxRepository
                        .findByAccountAndInterestYearOrderByCreditedAtDesc(a, year).size())
                .sum();

        BigDecimal nrwht;
        BigDecimal riwt;

        boolean isExempt = riwtExemptionRepository.existsByUserAndTaxYearAndStatus(
                user, year, RiwtExemptionStatus.APPROVED);

        if (isExempt || user.isSeniorCitizen()) {
            nrwht = BigDecimal.ZERO;
            riwt  = BigDecimal.ZERO;
        } else if (!user.isResident() || user.getTinNumber() == null || user.getTinNumber().isBlank()) {
            nrwht = totalActuallyWithheld;
            riwt  = BigDecimal.ZERO;
        } else {
            nrwht = BigDecimal.ZERO;
            riwt  = totalActuallyWithheld;
        }

        String exemptionReason = null;
        if (user.isSeniorCitizen()) {
            exemptionReason = "SENIOR_CITIZEN_EXEMPTION";
        } else if (isExempt) {
            exemptionReason = "RIWT_EXEMPTION";
        }

        return UserInterestSummary.builder()
                .user(user)
                .taxYear(year)
                .grossInterestEarned(gross)
                .nrwhtWithheld(nrwht)
                .riwtWithheld(riwt)
                .netInterestPaid(net)
                .exemptionReason(exemptionReason)
                .interestTransactionCount(txnCount)
                .build();
    }
}
