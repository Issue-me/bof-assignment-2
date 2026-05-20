package com.bof.banking.controller;

import com.bof.banking.model.Account;
import com.bof.banking.model.SavingsInterestTransaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.SavingsInterestTransactionRepository;
import com.bof.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer-facing interest history and summary endpoints.
 *
 * Endpoints:
 *   GET /api/accounts/{accountNumber}/interest-history
 *   GET /api/accounts/{accountNumber}/interest-summary?year=2026
 *   GET /api/accounts/interest-summary?year=2026   (all savings accounts for user)
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountInterestController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final AccountRepository                    accountRepository;
    private final SavingsInterestTransactionRepository interestTxRepository;
    private final UserRepository                       userRepository;

    // ── GET /api/accounts/{accountNumber}/interest-history ───────────────────

    @GetMapping("/{accountNumber}/interest-history")
        /**
         * Returns interest credit history for one customer-owned account.
         *
         * @param accountNumber account number in the path.
         * @param userDetails authenticated user context.
         * @return history rows ordered from newest to oldest.
         */
    public ResponseEntity<List<Map<String, Object>>> getInterestHistory(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails userDetails) {

        Account account = getOwnedAccount(userDetails.getUsername(), accountNumber);

        List<Map<String, Object>> rows =
                interestTxRepository.findByAccountOrderByCreditedAtDesc(account)
                        .stream()
                        .map(this::toMap)
                        .collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    // ── GET /api/accounts/{accountNumber}/interest-summary?year=2026 ─────────

    @GetMapping("/{accountNumber}/interest-summary")
        /**
         * Returns yearly interest totals for one customer-owned account.
         *
         * @param accountNumber account number in the path.
         * @param year target year; defaults to current year when 0 or omitted.
         * @param userDetails authenticated user context.
         * @return summary map with gross, RIWT deducted, and net interest values.
         */
    public ResponseEntity<Map<String, Object>> getInterestSummary(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int year,
            @AuthenticationPrincipal UserDetails userDetails) {

        Account account = getOwnedAccount(userDetails.getUsername(), accountNumber);
        int y = year > 0 ? year : LocalDate.now().getYear();

        BigDecimal grossInterest = interestTxRepository
                .sumGrossInterestByAccountAndYear(account, y);
        BigDecimal riwtDeducted  = interestTxRepository
                .sumRiwtByAccountAndYear(account, y);
        BigDecimal netInterest   = interestTxRepository
                .sumNetInterestByAccountAndYear(account, y);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountNumber", account.getAccountNumber());
        result.put("year",          y);
        result.put("annualRate",    account.getInterestRate());
        result.put("grossInterest", grossInterest);
        result.put("riwtDeducted",  riwtDeducted);
        result.put("netInterest",   netInterest);

        return ResponseEntity.ok(result);
    }

    // ── GET /api/accounts/interest-summary?year=2026 ─────────────────────────

    @GetMapping("/interest-summary")
        /**
         * Returns yearly interest totals across all savings accounts for the customer.
         *
         * @param year target year; defaults to current year when 0 or omitted.
         * @param userDetails authenticated user context.
         * @return aggregated totals plus per-account breakdown.
         */
    public ResponseEntity<Map<String, Object>> getAllAccountsInterestSummary(
            @RequestParam(defaultValue = "0") int year,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        int y = year > 0 ? year : LocalDate.now().getYear();

        // FIX: compare against AccountType enum, not a String
        List<Account> savingsAccounts = accountRepository.findByUser(user)
                .stream()
                .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()))
                .collect(Collectors.toList());

        BigDecimal tg = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumGrossInterestByAccountAndYear(a, y))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tr = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumRiwtByAccountAndYear(a, y))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tn = savingsAccounts.stream()
                .map(a -> interestTxRepository.sumNetInterestByAccountAndYear(a, y))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> accounts = savingsAccounts.stream().map(account -> {
            BigDecimal gross = interestTxRepository.sumGrossInterestByAccountAndYear(account, y);
            BigDecimal riwt  = interestTxRepository.sumRiwtByAccountAndYear(account, y);
            BigDecimal net   = interestTxRepository.sumNetInterestByAccountAndYear(account, y);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountNumber", account.getAccountNumber());
            m.put("accountName",   account.getAccountName());
            m.put("annualRate",    account.getInterestRate());
            m.put("grossInterest", gross);
            m.put("riwtDeducted",  riwt);
            m.put("netInterest",   net);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("year",             y);
        response.put("accounts",         accounts);
        response.put("totalGross",        tg);
        response.put("totalRiwtDeducted", tr);
        response.put("totalNetInterest",  tn);

        return ResponseEntity.ok(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

        /**
         * Resolves and validates that the given account belongs to the authenticated user.
         *
         * @param userEmail authenticated user email.
         * @param accountNumber account number to validate.
         * @return owned account entity.
         */
    private Account getOwnedAccount(String userEmail, String accountNumber) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return account;
    }

        /**
         * Maps one interest transaction to the response row expected by the UI.
         *
         * @param t interest transaction entity.
         * @return response-friendly map of transaction details.
         */
    private Map<String, Object> toMap(SavingsInterestTransaction t) {
        String monthName = LocalDate.of(t.getInterestYear(), t.getInterestMonth(), 1)
                .getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",              t.getId());
        m.put("period",          monthName + " " + t.getInterestYear());
        m.put("month",           t.getInterestMonth());
        m.put("year",            t.getInterestYear());
        m.put("balanceSnapshot", t.getBalanceSnapshot());
        m.put("annualRate",      t.getAnnualRate());
        m.put("grossInterest",   t.getGrossInterest());
        m.put("riwtDeducted",    t.getRiwtDeducted());
        m.put("netInterest",     t.getNetInterest());
        m.put("riwtExempt",      t.isRiwtExempt());
        m.put("reference",       t.getReferenceNumber());
        m.put("creditedAt",      t.getCreditedAt() != null
                                     ? t.getCreditedAt().format(DT_FMT) : null);
        return m;
    }
}