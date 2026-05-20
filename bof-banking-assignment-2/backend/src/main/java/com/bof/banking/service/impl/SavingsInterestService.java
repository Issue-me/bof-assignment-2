// package com.bof.banking.service.impl;

// import com.bof.banking.exception.ResourceNotFoundException;
// import com.bof.banking.model.Account;
// import com.bof.banking.model.InterestRate;
// import com.bof.banking.model.SavingsInterestTransaction;
// import com.bof.banking.model.Transaction;
// import com.bof.banking.model.enums.AccountType;
// import com.bof.banking.model.enums.PaymentStatus;
// import com.bof.banking.model.enums.RiwtExemptionStatus;
// import com.bof.banking.model.enums.TransactionType;
// import com.bof.banking.repository.AccountRepository;
// import com.bof.banking.repository.InterestRateRepository;
// import com.bof.banking.repository.RiwtExemptionRepository;
// import com.bof.banking.repository.SavingsInterestTransactionRepository;
// import com.bof.banking.repository.TransactionRepository;
// import com.bof.banking.service.notification.NotificationService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.time.LocalDateTime;
// import java.time.format.TextStyle;
// import java.util.List;
// import java.util.Locale;
// import java.util.Optional;

// /**
//  * Savings Interest Service
//  *
//  * Credits monthly interest to every active customer SAVINGS account.
//  * Rate is looked up from the interest_rates table (set by teller via Manage Interest Rates page).
//  * Initial default rate is 3.5% p.a. (seeded by migration).
//  *
//  * Formula:  grossInterest = balance × (annualRate / 12)
//  * RIWT:     10% deducted unless customer has approved FRCS exemption.
//  * Funding:  BOF90000001 debited net amount; RIWT portion stays for FRCS remittance.
//  *
//  * IMPORTANT: Each credit also writes a Transaction record (type=INTEREST) so that
//  * TaxServiceImpl.getReport() can find it when building the tax report.
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class SavingsInterestService {

//     private static final BigDecimal RIWT_RATE     = new BigDecimal("0.10");
//     private static final int        SCALE          = 2;
//     private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);

//     @Value("${app.bank.internal-account-number:BOF90000001}")
//     private String bankInternalAccountNumber;

//     private final AccountRepository                    accountRepository;
//     private final SavingsInterestTransactionRepository interestTxRepository;
//     private final RiwtExemptionRepository              riwtExemptionRepository;
//     private final InterestRateRepository               interestRateRepository;
//     private final TransactionRepository                transactionRepository;
//     private final NotificationService                  notificationService;

//     // ── Manual trigger (from admin controller / scheduler) ───────────────────

//     @Transactional
//     public InterestRunResult runManual(Integer overrideMonth, Integer overrideYear) {
//         LocalDate now = LocalDate.now();
//         int month = overrideMonth != null ? overrideMonth : now.getMonthValue();
//         int year  = overrideYear  != null ? overrideYear  : now.getYear();
//         log.info("=== Savings interest run: period={}/{} ===", month, year);
//         return creditMonthlyInterest(month, year);
//     }

//     // ── Core logic ────────────────────────────────────────────────────────────

//     @Transactional
//     public InterestRunResult creditMonthlyInterest(int month, int year) {

//         // Look up rate for the last day of the target month
//         LocalDate lastDay = LocalDate.of(year, month, 1)
//                 .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth());
//         Optional<InterestRate> rateOpt =
//                 interestRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(lastDay);

//         if (rateOpt.isEmpty()) {
//             String warn = "No interest rate configured for " + month + "/" + year
//                     + ". Set a rate via the Manage Interest Rates page.";
//             log.warn(warn);
//             return new InterestRunResult(month, year, 0, 0, 0, 0,
//                     BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, warn);
//         }

//         BigDecimal annualRate = rateOpt.get().getAnnualRate();
//         log.info("Rate {}% for period {}/{}",
//                 annualRate.multiply(BigDecimal.valueOf(100)).toPlainString(), month, year);

//         // Bank internal funding account
//         Account bankAccount = accountRepository.findByAccountNumber(bankInternalAccountNumber)
//                 .orElseThrow(() -> new ResourceNotFoundException(
//                         "Bank internal account not found: " + bankInternalAccountNumber));

//         // All active customer SAVINGS accounts (exclude internal)
//         List<Account> savingsAccounts = accountRepository
//                 .findByAccountTypeAndIsActiveTrue(AccountType.SAVINGS).stream()
//                 .filter(a -> !a.getAccountNumber().equals(bankInternalAccountNumber))
//                 .filter(a -> a.getUser() != null)
//                 .toList();

//         log.info("Processing {} SAVINGS accounts for {}/{}", savingsAccounts.size(), month, year);

//         int credited = 0, skipped = 0, duplicate = 0;
//         BigDecimal totalGross = BigDecimal.ZERO;
//         BigDecimal totalRiwt  = BigDecimal.ZERO;
//         BigDecimal totalNet   = BigDecimal.ZERO;

//         for (Account account : savingsAccounts) {

//             if (interestTxRepository.existsByAccountAndInterestMonthAndInterestYear(account, month, year)) {
//                 duplicate++;
//                 continue;
//             }

//             BigDecimal balance = account.getBalance();
//             if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) { skipped++; continue; }

//             BigDecimal grossInterest = balance
//                     .multiply(annualRate.divide(MONTHS_IN_YEAR, 10, RoundingMode.HALF_UP))
//                     .setScale(SCALE, RoundingMode.HALF_UP);
//             if (grossInterest.compareTo(BigDecimal.ZERO) <= 0) { skipped++; continue; }

//             boolean riwtExempt = riwtExemptionRepository.existsByUserAndTaxYearAndStatus(
//                     account.getUser(), year, RiwtExemptionStatus.APPROVED);
//             BigDecimal riwtDeducted = riwtExempt ? BigDecimal.ZERO
//                     : grossInterest.multiply(RIWT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
//             BigDecimal netInterest = grossInterest.subtract(riwtDeducted);

//             if (bankAccount.getBalance().compareTo(netInterest) < 0) {
//                 throw new RuntimeException("Bank account BOF90000001 has insufficient funds. " +
//                         "Available: FJD " + bankAccount.getBalance() + ", Required: FJD " + netInterest);
//             }

//             String suffix = account.getAccountNumber().length() >= 8
//                     ? account.getAccountNumber().substring(account.getAccountNumber().length() - 8)
//                     : account.getAccountNumber();
//             String ref = String.format("INT-%d-%02d-%s", year, month, suffix);

//             // 1. Persist SavingsInterestTransaction audit record
//             interestTxRepository.save(SavingsInterestTransaction.builder()
//                     .account(account).balanceSnapshot(balance).annualRate(annualRate)
//                     .grossInterest(grossInterest).riwtDeducted(riwtDeducted)
//                     .netInterest(netInterest).interestMonth(month).interestYear(year)
//                     .riwtExempt(riwtExempt).referenceNumber(ref).build());

//             // 2. Write a Transaction record (type=INTEREST) so TaxServiceImpl finds it
//             //    The GROSS amount is recorded — RIWT is shown as a separate deduction in the tax report
//             transactionRepository.save(Transaction.builder()
//                     .referenceNumber(ref)
//                     .transactionType(TransactionType.INTEREST)
//                     .amount(grossInterest)   // gross — tax report deducts RIWT separately
//                     .description("Monthly savings interest " + month + "/" + year
//                             + (riwtExempt ? " (RIWT exempt)" : " (RIWT deducted: FJD " + riwtDeducted + ")"))
//                     .destinationAccount(account)
//                     .sourceAccount(bankAccount)
//                     .status(PaymentStatus.COMPLETED)
//                     .balanceAfter(account.getBalance().add(netInterest))
//                     .transactionDate(LocalDateTime.of(year, month,
//                             LocalDate.of(year, month, 1).lengthOfMonth(), 23, 59))
//                     .build());

//             // 3. Credit customer (net only)
//             account.setBalance(account.getBalance().add(netInterest));
//             BigDecimal earned = account.getInterestEarned() != null ? account.getInterestEarned() : BigDecimal.ZERO;
//             account.setInterestEarned(earned.add(grossInterest));
//             accountRepository.save(account);

//             // 4. Debit bank internal account (net only)
//             bankAccount.setBalance(bankAccount.getBalance().subtract(netInterest));

//             // 5. Notify customer
//             sendInterestNotification(account, grossInterest, riwtDeducted, netInterest,
//                     riwtExempt, annualRate, month, year, ref);

//             totalGross = totalGross.add(grossInterest);
//             totalRiwt  = totalRiwt.add(riwtDeducted);
//             totalNet   = totalNet.add(netInterest);
//             credited++;
//         }

//         accountRepository.save(bankAccount);
//         log.info("=== Complete: {}/{} credited={} skipped={} dup={} gross={} riwt={} net={}",
//                 month, year, credited, skipped, duplicate, totalGross, totalRiwt, totalNet);

//         return new InterestRunResult(month, year, credited, skipped, 0, duplicate,
//                 totalGross, totalRiwt, totalNet, null);
//     }

//     // ── Helpers ───────────────────────────────────────────────────────────────

//     private void sendInterestNotification(Account account, BigDecimal gross, BigDecimal riwt,
//                                            BigDecimal net, boolean exempt, BigDecimal rate,
//                                            int month, int year, String ref) {
//         try {
//             String monthName = LocalDate.of(year, month, 1)
//                     .getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
//             String pct  = rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
//             String acct = mask(account.getAccountNumber());
//             String title, message;
//             if (riwt.compareTo(BigDecimal.ZERO) > 0) {
//                 title = String.format("Interest credited — FJD %.2f (net) for %s %d", net, monthName, year);
//                 message = String.format(
//                     "Monthly savings interest of FJD %.2f has been credited to account %s for %s %d. " +
//                     "Gross: FJD %.2f at %s p.a. RIWT deducted: FJD %.2f (10%%). Net credited: FJD %.2f. Ref: %s. " +
//                     "To apply for RIWT exemption, upload your FRCS Certificate via your Tax Report page.",
//                     net, acct, monthName, year, gross, pct, riwt, net, ref);
//             } else {
//                 title = String.format("Interest credited — FJD %.2f for %s %d (RIWT exempt)", net, monthName, year);
//                 message = String.format(
//                     "Monthly savings interest of FJD %.2f has been credited to account %s for %s %d. " +
//                     "Gross: FJD %.2f at %s p.a. No RIWT deducted — your exemption is active. Ref: %s.",
//                     net, acct, monthName, year, gross, pct, ref);
//             }
//             notificationService.notifyInterestCredited(account.getUser().getEmail(), title, message);
//         } catch (Exception e) {
//             log.warn("Notification failed for {}: {}", account.getAccountNumber(), e.getMessage());
//         }
//     }

//     private String mask(String n) {
//         return (n == null || n.length() < 4) ? "****" : "****" + n.substring(n.length() - 4);
//     }

//     public record InterestRunResult(
//             int month, int year,
//             int accountsCredited, int accountsSkipped,
//             int zeroRateSkipped, int duplicatesSkipped,
//             BigDecimal totalGrossInterest, BigDecimal totalRiwtDeducted,
//             BigDecimal totalNetInterestPaid, String warningMessage) {

//         public InterestRunResult(int month, int year, int accountsCredited, int accountsSkipped,
//                 int zeroRateSkipped, int duplicatesSkipped, BigDecimal totalGrossInterest,
//                 BigDecimal totalRiwtDeducted, BigDecimal totalNetInterestPaid) {
//             this(month, year, accountsCredited, accountsSkipped, zeroRateSkipped,
//                  duplicatesSkipped, totalGrossInterest, totalRiwtDeducted, totalNetInterestPaid, null);
//         }

//         public String summary() {
//             if (warningMessage != null) return warningMessage;
//             String mn = LocalDate.of(year, month, 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
//             return String.format("Interest run for %s %d: %d accounts credited, " +
//                     "gross=FJD %.2f, riwt=FJD %.2f, net=FJD %.2f. Skipped: %d. Duplicates: %d.",
//                     mn, year, accountsCredited, totalGrossInterest, totalRiwtDeducted,
//                     totalNetInterestPaid, accountsSkipped, duplicatesSkipped);
//         }
//     }
// }

package com.bof.banking.service.impl;

import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.Account;
import com.bof.banking.model.InterestRate;
import com.bof.banking.model.SavingsInterestTransaction;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.RiwtExemptionStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.InterestRateRepository;
import com.bof.banking.repository.RiwtExemptionRepository;
import com.bof.banking.repository.SavingsInterestTransactionRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Savings Interest Service
 *
 * Credits monthly interest to every active customer SAVINGS account.
 * Debits the net interest total from BANK_INTERNAL_OPERATIONS (BOF90000001).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ROOT CAUSE — why balances were not updating
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * BANK_INTERNAL_OPERATIONS is account type SAVINGS.
 *
 * When findByAccountTypeAndIsActiveTrue(SAVINGS) runs it loads ALL savings
 * accounts — including the bank account — into Hibernate's first-level
 * (session) cache as managed entities at their current DB balance.
 *
 * We filter it out of the customer loop, but the session cache still has a
 * reference to it at its ORIGINAL balance. Then, every time we call:
 *
 *   accountRepository.save(customerAccount)     ← triggers flush
 *   interestTxRepository.save(...)              ← triggers flush
 *   transactionRepository.save(...)             ← triggers flush
 *
 * ...Hibernate flushes the session. During a flush, Hibernate re-synchronises
 * ALL managed entities. The bankAccount entity in the cache still has its
 * original balance (because we only updated the in-memory object, not the DB
 * yet). Hibernate sees no dirty state on it and does NOT write it — BUT on
 * the next read Hibernate may return the cached instance, which has been reset
 * to its original balance by the flush cycle.
 *
 * So every bankAccount.setBalance(runningBankBalance) call inside the loop
 * gets silently overwritten on the next flush, and the final
 * accountRepository.save(bankAccount) writes the original pre-run balance.
 *
 * Customer account balances had a similar but subtler problem: save() adds
 * the entity to the write-behind queue but doesn't guarantee immediate SQL
 * execution. If the transaction commits before SQL is flushed, the balance
 * in the DB remains unchanged.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE FIX
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 1. Fetch the bank account with findByIdForUpdate() — PESSIMISTIC_WRITE lock.
 *    This tells Hibernate: "this entity is exclusively mine for this transaction,
 *    do NOT reload it from the cache or merge a stale snapshot over it."
 *
 * 2. Use saveAndFlush() for every customer account write AND for the bank
 *    account after every deduction. saveAndFlush() executes the SQL UPDATE
 *    immediately within the current transaction, so the DB row reflects the
 *    new balance right away and Hibernate's dirty-tracking stays in sync.
 *
 * 3. Keep runningBankBalance as the arithmetic tracker — it is always correct
 *    regardless of what Hibernate does. bankAccount.setBalance() is always
 *    set from runningBankBalance immediately before each saveAndFlush call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsInterestService {

    private static final BigDecimal RIWT_RATE     = new BigDecimal("0.10");
    private static final int        SCALE          = 2;
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);

    @Value("${app.bank.internal-account-number:BOF90000001}")
    private String bankInternalAccountNumber;

    private final AccountRepository                    accountRepository;
    private final SavingsInterestTransactionRepository interestTxRepository;
    private final RiwtExemptionRepository              riwtExemptionRepository;
    private final InterestRateRepository               interestRateRepository;
    private final TransactionRepository                transactionRepository;
    private final NotificationService                  notificationService;

    // ── Manual trigger ────────────────────────────────────────────────────────

    @Transactional
    /**
     * Handles run manual.
     * @param overrideMonth the date or time value used by this operation.
     * @param overrideYear the date or time value used by this operation.
     * @return the result of the operation.
     */
    public InterestRunResult runManual(Integer overrideMonth, Integer overrideYear) {
        LocalDate now = LocalDate.now();
        int month = overrideMonth != null ? overrideMonth : now.getMonthValue();
        int year  = overrideYear  != null ? overrideYear  : now.getYear();
        log.info("=== Savings interest run requested: period={}/{} ===", month, year);
        return creditMonthlyInterest(month, year);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    @Transactional
    /**
     * Handles credit monthly interest.
     * @param month the date or time value used by this operation.
     * @param year the date or time value used by this operation.
     * @return the result of the operation.
     */
    public InterestRunResult creditMonthlyInterest(int month, int year) {

        // ── 1. Resolve interest rate ──────────────────────────────────────────
        LocalDate lastDay = LocalDate.of(year, month, 1)
                .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth());

        Optional<InterestRate> rateOpt = interestRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(lastDay);

        if (rateOpt.isEmpty()) {
            String warn = "No interest rate configured for " + month + "/" + year
                    + ". Set a rate via Manage Interest Rates.";
            log.warn(warn);
            return new InterestRunResult(month, year, 0, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, warn);
        }

        BigDecimal annualRate = rateOpt.get().getAnnualRate();
        log.info("Using rate {}% p.a. for period {}/{}",
                annualRate.multiply(BigDecimal.valueOf(100)).toPlainString(), month, year);

        // ── 2. Load bank account with pessimistic write lock ──────────────────
        //
        // FIX: findByIdForUpdate acquires a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE)
        // lock. This prevents any other concurrent transaction from modifying the
        // bank account row AND tells Hibernate not to merge a stale first-level
        // cache snapshot over the locked entity during subsequent flushes.
        //
        Account bankAccountRef = accountRepository.findByAccountNumber(bankInternalAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank internal account not found: " + bankInternalAccountNumber));

        Account bankAccount = accountRepository.findByIdForUpdate(bankAccountRef.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank internal account lock failed for id: " + bankAccountRef.getId()));

        log.info("Bank account {} locked. Starting balance: FJD {}",
                bankInternalAccountNumber, bankAccount.getBalance());

        // ── 3. Local balance tracker ──────────────────────────────────────────
        //
        // runningBankBalance is the single source of truth for arithmetic.
        // We NEVER call bankAccount.getBalance() after this line — only
        // runningBankBalance. This is correct even if Hibernate does
        // something unexpected with the entity mid-transaction.
        //
        BigDecimal runningBankBalance = bankAccount.getBalance();

        // ── 4. Load customer SAVINGS accounts ─────────────────────────────────
        List<Account> savingsAccounts = accountRepository
                .findByAccountTypeAndIsActiveTrue(AccountType.SAVINGS)
                .stream()
                .filter(a -> !a.getAccountNumber().equals(bankInternalAccountNumber))
                .filter(a -> a.getUser() != null)
                .toList();

        log.info("Processing {} customer SAVINGS accounts for {}/{}",
                savingsAccounts.size(), month, year);

        int credited = 0, skipped = 0, duplicate = 0;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRiwt  = BigDecimal.ZERO;
        BigDecimal totalNet   = BigDecimal.ZERO;

        // ── 5. Process each account ───────────────────────────────────────────
        for (Account account : savingsAccounts) {

            // Idempotency guard
            if (interestTxRepository.existsByAccountAndInterestMonthAndInterestYear(
                    account, month, year)) {
                log.debug("Duplicate skip: account={} period={}/{}", account.getAccountNumber(), month, year);
                duplicate++;
                continue;
            }

            BigDecimal balance = account.getBalance();
            if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Zero/null balance skip: account={}", account.getAccountNumber());
                skipped++;
                continue;
            }

            // Interest calculation
            BigDecimal grossInterest = balance
                    .multiply(annualRate.divide(MONTHS_IN_YEAR, 10, RoundingMode.HALF_UP))
                    .setScale(SCALE, RoundingMode.HALF_UP);

            if (grossInterest.compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }

            // RIWT
            boolean riwtExempt = riwtExemptionRepository.existsByUserAndTaxYearAndStatus(
                    account.getUser(), year, RiwtExemptionStatus.APPROVED);
            BigDecimal riwtDeducted = riwtExempt
                    ? BigDecimal.ZERO
                    : grossInterest.multiply(RIWT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal netInterest = grossInterest.subtract(riwtDeducted);

            // Insufficient funds check — uses runningBankBalance, never bankAccount.getBalance()
            if (runningBankBalance.compareTo(netInterest) < 0) {
                String msg = String.format(
                    "BANK_INTERNAL_OPERATIONS (%s) has insufficient funds. " +
                    "Available: FJD %s, Required: FJD %s for account %s. " +
                    "Accounts credited so far: %d.",
                    bankInternalAccountNumber, runningBankBalance,
                    netInterest, account.getAccountNumber(), credited);
                log.error(msg);
                // Ensure what we've done so far is flushed before throwing
                bankAccount.setBalance(runningBankBalance);
                accountRepository.saveAndFlush(bankAccount);
                throw new RuntimeException(msg);
            }

            // Reference
            String suffix = account.getAccountNumber().length() >= 8
                    ? account.getAccountNumber().substring(account.getAccountNumber().length() - 8)
                    : account.getAccountNumber();
            String ref = String.format("INT-%d-%02d-%s", year, month, suffix);

            // Step A — audit record
            interestTxRepository.save(SavingsInterestTransaction.builder()
                    .account(account)
                    .balanceSnapshot(balance)
                    .annualRate(annualRate)
                    .grossInterest(grossInterest)
                    .riwtDeducted(riwtDeducted)
                    .netInterest(netInterest)
                    .interestMonth(month)
                    .interestYear(year)
                    .riwtExempt(riwtExempt)
                    .referenceNumber(ref)
                    .build());

            // Step B — transaction ledger entry
            BigDecimal newCustomerBalance = balance.add(netInterest);
            transactionRepository.save(Transaction.builder()
                    .referenceNumber(ref)
                    .transactionType(TransactionType.INTEREST)
                    .amount(grossInterest)
                    .description("Monthly savings interest " + month + "/" + year
                            + (riwtExempt
                                ? " (RIWT exempt)"
                                : " (RIWT deducted: FJD " + riwtDeducted + ")"))
                    .destinationAccount(account)
                    .sourceAccount(bankAccount)
                    .status(PaymentStatus.COMPLETED)
                    .balanceAfter(newCustomerBalance)
                    .transactionDate(LocalDateTime.of(year, month,
                            LocalDate.of(year, month, 1).lengthOfMonth(), 23, 59))
                    .build());

            // Step C — credit customer
            account.setBalance(newCustomerBalance);
            account.setInterestEarned(
                (account.getInterestEarned() != null ? account.getInterestEarned() : BigDecimal.ZERO)
                    .add(grossInterest));
            // Keep stored rate in sync with live RBF rate
            account.setInterestRate(annualRate);

            // FIX: saveAndFlush — executes UPDATE SQL immediately within this transaction.
            // Without flush, the balance change sits in Hibernate's write-behind queue and
            // may not reach the DB before the transaction commits, leaving the old balance.
            accountRepository.saveAndFlush(account);

            log.debug("Credited: account={} gross={} riwt={} net={} newBalance={}",
                    account.getAccountNumber(), grossInterest, riwtDeducted, netInterest, newCustomerBalance);

            // Step D — deduct from bank account and flush immediately
            //
            // FIX: This is the critical missing step. We must saveAndFlush the bank account
            // after EVERY deduction — not once at the end. Without this, Hibernate's flush
            // during the next customer's save() can reload the bank account from the
            // first-level cache snapshot (original balance) and overwrite our setBalance() call.
            //
            runningBankBalance = runningBankBalance.subtract(netInterest);
            bankAccount.setBalance(runningBankBalance);
            accountRepository.saveAndFlush(bankAccount);

            log.debug("Bank deducted: netInterest={} bankRemainingBalance={}",
                    netInterest, runningBankBalance);

            // Step E — notify
            sendInterestNotification(account, grossInterest, riwtDeducted, netInterest,
                    riwtExempt, annualRate, month, year, ref);

            totalGross = totalGross.add(grossInterest);
            totalRiwt  = totalRiwt.add(riwtDeducted);
            totalNet   = totalNet.add(netInterest);
            credited++;
        }

        // ── 6. Final flush — ensures clean state ──────────────────────────────
        //
        // By this point every saveAndFlush above has already written the correct
        // balance. This is a safety net for the case where the loop processes
        // zero accounts (all duplicates / all skipped).
        //
        bankAccount.setBalance(runningBankBalance);
        accountRepository.saveAndFlush(bankAccount);

        log.info("=== Interest run complete: period={}/{} credited={} skipped={} duplicates={} " +
                 "totalGross={} totalRiwt={} totalNet={} bankFinalBalance={}",
                month, year, credited, skipped, duplicate,
                totalGross, totalRiwt, totalNet, runningBankBalance);

        return new InterestRunResult(month, year, credited, skipped, 0, duplicate,
                totalGross, totalRiwt, totalNet, null);
    }

    // ── Notification helper ───────────────────────────────────────────────────

    private void sendInterestNotification(Account account, BigDecimal gross, BigDecimal riwt,
                                           BigDecimal net, boolean exempt, BigDecimal rate,
                                           int month, int year, String ref) {
        try {
            String monthName = LocalDate.of(year, month, 1)
                    .getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String pct  = rate.multiply(BigDecimal.valueOf(100))
                              .stripTrailingZeros().toPlainString() + "%";
            String acct = mask(account.getAccountNumber());
            String title, message;

            if (riwt.compareTo(BigDecimal.ZERO) > 0) {
                title = String.format(
                    "Interest credited — FJD %.2f (net) for %s %d", net, monthName, year);
                message = String.format(
                    "Monthly savings interest of FJD %.2f has been credited to account %s " +
                    "for %s %d. Gross: FJD %.2f at %s p.a. " +
                    "RIWT deducted: FJD %.2f (10%%). Net credited: FJD %.2f. Ref: %s. " +
                    "To apply for RIWT exemption, upload your FRCS Certificate via your Tax Report page.",
                    net, acct, monthName, year, gross, pct, riwt, net, ref);
            } else {
                title = String.format(
                    "Interest credited — FJD %.2f for %s %d (RIWT exempt)", net, monthName, year);
                message = String.format(
                    "Monthly savings interest of FJD %.2f has been credited to account %s " +
                    "for %s %d. Gross: FJD %.2f at %s p.a. " +
                    "No RIWT deducted — your exemption is active. Ref: %s.",
                    net, acct, monthName, year, gross, pct, ref);
            }

            notificationService.notifyInterestCredited(
                    account.getUser().getEmail(), title, message);

        } catch (Exception e) {
            log.error("Notification FAILED for account={}: {} — {}",
                    account.getAccountNumber(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private String mask(String n) {
        return (n == null || n.length() < 4) ? "****" : "****" + n.substring(n.length() - 4);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Encapsulates business rules for i nt er es tr un re su lt and keeps controller logic thin.
     */
    public record InterestRunResult(
            int month, int year,
            int accountsCredited, int accountsSkipped,
            int zeroRateSkipped,  int duplicatesSkipped,
            BigDecimal totalGrossInterest,
            BigDecimal totalRiwtDeducted,
            BigDecimal totalNetInterestPaid,
            String warningMessage) {

        public InterestRunResult(int month, int year,
                int accountsCredited, int accountsSkipped,
                int zeroRateSkipped,  int duplicatesSkipped,
                BigDecimal totalGrossInterest,
                BigDecimal totalRiwtDeducted,
                BigDecimal totalNetInterestPaid) {
            this(month, year, accountsCredited, accountsSkipped, zeroRateSkipped,
                 duplicatesSkipped, totalGrossInterest, totalRiwtDeducted,
                 totalNetInterestPaid, null);
        }

        /**
         * Handles summary.
         * @return the resulting text value.
         */
        public String summary() {
            if (warningMessage != null) return warningMessage;
            String mn = LocalDate.of(year, month, 1)
                    .getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            return String.format(
                "Interest run for %s %d: %d accounts credited, " +
                "gross=FJD %.2f, riwt=FJD %.2f, net=FJD %.2f. " +
                "Skipped: %d. Duplicates: %d.",
                mn, year, accountsCredited,
                totalGrossInterest, totalRiwtDeducted, totalNetInterestPaid,
                accountsSkipped, duplicatesSkipped);
        }
    }
}
