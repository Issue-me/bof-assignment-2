package com.bof.banking.service.impl;

import com.bof.banking.config.BankingProperties;
import com.bof.banking.dto.account.AccountRequest;
import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.dto.account.AccountResponseDto;
import com.bof.banking.dto.account.AccountSummaryResponse;
import com.bof.banking.dto.account.CreateAccountRequestDto;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.InsufficientFundsException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.AccountMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.InterestRate;
import com.bof.banking.model.Role;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.InterestRateRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AccountServiceImpl — bank account management.
 *
 * Interest rate strategy for SAVINGS accounts:
 *
 *   The interest rate displayed to users always comes from the interest_rates
 *   table (set by tellers via the Interest Rate Manager). The rate stored on
 *   account.interestRate is only a snapshot written at creation time and is
 *   NOT updated automatically when the teller changes the rate.
 *
 *   resolveLiveSavingsRate() fetches the rate effective on or before today
 *   from interest_rates — the same query used by SavingsInterestService and
 *   TaxServiceImpl — and substitutes it into every DTO we return.
 *
 *   Default: DataSeeder seeds 3.5% p.a. effective from Jan 1 of the current
 *   year. Tellers can add new rates at any time from today onwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final BigDecimal MONTHLY_MAINTENANCE_FEE = new BigDecimal("2.50");
    private static final BigDecimal NRWHT_RATE               = new BigDecimal("0.10");
    private static final BigDecimal MONTHS_IN_YEAR           = new BigDecimal("12");

    private final AccountRepository      accountRepository;
    private final UserRepository         userRepository;
    private final AccountMapper          accountMapper;
    private final BankingProperties      bankingProperties;
    private final InterestRateRepository interestRateRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Creates account data.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public AccountResponseDto createAccount(String userEmail, CreateAccountRequestDto request) {
        User requester = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User accountOwner = resolveAccountOwner(requester, request.getCustomerUserId());

        BigDecimal initialDeposit = request.getInitialDeposit() != null
                ? request.getInitialDeposit() : BigDecimal.ZERO;

        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0)
            throw new BadRequestException("Initial deposit cannot be negative");

        // For SAVINGS accounts, store the live rate at creation time so the
        // account.interestRate field is accurate from day one.
        BigDecimal rateToStore = getDefaultRateForType(request.getAccountType());
        if (request.getAccountType() == AccountType.SAVINGS) {
            BigDecimal live = resolveLiveSavingsRate();
            if (live != null) rateToStore = live;
        }

        Account account = Account.builder()
                .accountNumber(generateAccountNumber())
            .accountName(normalizeAccountName(request.getAccountName()))
                .accountType(request.getAccountType())
                .balance(initialDeposit)
                .interestRate(rateToStore)
                .interestEarned(BigDecimal.ZERO)
                .isActive(true)
            .user(accountOwner)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Created account {} for owner {} by requester {}",
            saved.getAccountNumber(), accountOwner.getEmail(), requester.getEmail());
        return toResponseDtoWithLiveRate(saved, resolveLiveSavingsRate());
    }

    @Override
    @Transactional
    /**
     * Creates account data.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public AccountResponse createAccount(String userEmail, AccountRequest request) {
        AccountResponseDto created = createAccount(userEmail,
                CreateAccountRequestDto.builder()
                        .accountType(request.getAccountType())
                        .initialDeposit(request.getInitialDeposit())
                .accountName(request.getAccountName())
                        .build());

        AccountResponse response = AccountResponse.builder()
                .id(created.getId())
                .accountNumber(created.getAccountNumber())
                .accountName(request.getAccountName())
                .accountType(created.getAccountType())
                .balance(created.getBalance())
                .interestRate(created.getInterestRate())
                .interestEarned(created.getInterestEarned())
                .active(true)
                .createdAt(created.getCreatedAt())
                .build();
        return response;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns account by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        AccountResponse response = accountMapper.toResponse(account);
        patchLiveRateOnResponse(response, account);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns account by id data.
     * @param userEmail the email of the authenticated user.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponseDto getAccountById(String userEmail, Long id) {
        Account account = accountRepository.findByIdAndUser_Email(id, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        return toResponseDtoWithLiveRate(account, resolveLiveSavingsRate());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns account by number data.
     * @param accountNumber the account Number.
     * @return the result of the operation.
     */
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        AccountResponse response = accountMapper.toResponse(account);
        patchLiveRateOnResponse(response, account);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns accounts by user data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<AccountResponseDto> getAccountsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BigDecimal liveRate = resolveLiveSavingsRate();
        return accountRepository.findByUser(user).stream()
                .map(a -> toResponseDtoWithLiveRate(a, liveRate))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns all accounts data.
     * @return the matching results.
     */
    public List<AccountResponseDto> getAllAccounts() {
        BigDecimal liveRate = resolveLiveSavingsRate();
        return accountRepository.findAll().stream()
                .map(a -> toResponseDtoWithLiveRate(a, liveRate))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns active accounts by user data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<AccountResponse> getActiveAccountsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BigDecimal liveRate = resolveLiveSavingsRate();
        return accountRepository.findByUserAndIsActiveTrue(user).stream()
                .map(a -> {
                    AccountResponse r = accountMapper.toResponse(a);
                    patchLiveRateOnResponse(r, a);
                    return r;
                })
                .collect(Collectors.toList());
    }

    // ── Interest / fee operations ─────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles calculate savings interest.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponseDto calculateSavingsInterest(String userEmail, Long accountId) {
        Account account = accountRepository.findByIdAndUser_Email(accountId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getAccountType() != AccountType.SAVINGS)
            throw new BadRequestException("Interest can only be calculated for savings accounts");

        validateTinForTax(account);

        BigDecimal liveRate   = resolveLiveSavingsRate();
        BigDecimal rateToUse  = liveRate != null ? liveRate : account.getInterestRate();
        BigDecimal monthly    = calculateMonthlyInterest(account.getBalance(), rateToUse);

        if (monthly.compareTo(BigDecimal.ZERO) <= 0)
            return toResponseDtoWithLiveRate(account, liveRate);

        account.setInterestEarned(account.getInterestEarned().add(monthly));
        account.setBalance(account.getBalance().add(monthly));
        account.setInterestRate(rateToUse);         // keep stored rate in sync
        applyNrwhtTaxToAccount(account);

        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0)
            throw new InsufficientFundsException("Account balance cannot go negative");

        return toResponseDtoWithLiveRate(accountRepository.save(account), liveRate);
    }

    @Override
    @Transactional
    /**
     * Updates nrwhttax values.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponseDto applyNRWHTTax(String userEmail, Long accountId) {
        Account account = accountRepository.findByIdAndUser_Email(accountId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getAccountType() != AccountType.SAVINGS)
            throw new BadRequestException("NRWHT can only be applied to savings accounts");

        validateTinForTax(account);
        applyNrwhtTaxToAccount(account);

        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0)
            throw new InsufficientFundsException("Account balance cannot go negative");

        return toResponseDtoWithLiveRate(accountRepository.save(account), resolveLiveSavingsRate());
    }

    @Override
    @Transactional
    @Deprecated
    /**
     * Handles calculate interest.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponseDto calculateInterest(String userEmail, Long accountId) {
        return calculateSavingsInterest(userEmail, accountId);
    }

    @Override
    @Transactional
    /**
     * Updates monthly maintenance fee values.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponseDto applyMonthlyMaintenanceFee(String userEmail, Long accountId) {
        Account account = accountRepository.findByIdAndUser_Email(accountId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getAccountType() != AccountType.SIMPLE_ACCESS)
            return toResponseDtoWithLiveRate(account, resolveLiveSavingsRate());

        if (account.getBalance().compareTo(MONTHLY_MAINTENANCE_FEE) < 0)
            throw new InsufficientFundsException("Insufficient funds to apply monthly maintenance fee");

        account.setBalance(account.getBalance().subtract(MONTHLY_MAINTENANCE_FEE));
        return toResponseDtoWithLiveRate(accountRepository.save(account), resolveLiveSavingsRate());
    }

    @Override
    @Transactional
    /**
     * Updates monthly maintenance fees for all eligible accounts values.
     * @return the result of the operation.
     */
    public int applyMonthlyMaintenanceFeesForAllEligibleAccounts() {
        List<Account> simpleAccessAccounts = accountRepository
                .findByAccountTypeAndIsActiveTrue(AccountType.SIMPLE_ACCESS);
 
        int chargedCount = 0;
        int skipped      = 0;
 
        for (Account account : simpleAccessAccounts) {
            BigDecimal balance = account.getBalance();
            if (balance == null || balance.compareTo(MONTHLY_MAINTENANCE_FEE) < 0) {
                skipped++;
                log.warn("Skipping maintenance fee for {} — insufficient funds",
                        account.getAccountNumber());
                continue;
            }
            account.setBalance(balance.subtract(MONTHLY_MAINTENANCE_FEE));
            chargedCount++;
        }
 
        accountRepository.saveAll(simpleAccessAccounts);
        log.info("Fixed-date maintenance fee run: charged={} skipped={}", chargedCount, skipped);
        return chargedCount;
    }

    // ── Update / close ────────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Updates account values.
     * @param id the unique identifier of the target record.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public AccountResponse updateAccount(Long id, AccountRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        account.setAccountName(request.getAccountName());
        Account saved = accountRepository.save(account);
        AccountResponse response = accountMapper.toResponse(saved);
        patchLiveRateOnResponse(response, saved);
        return response;
    }

    @Override
    @Transactional
    /**
     * Handles close account.
     * @param id the unique identifier of the target record.
     */
    public void closeAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        account.setActive(false);
        accountRepository.save(account);
        log.info("Account {} closed", account.getAccountNumber());
    }

    @Override
    @Transactional
    /**
     * Handles activate account.
     * @param id the unique identifier of the target record.
     */
    public void activateAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        account.setActive(true);
        accountRepository.save(account);
        log.info("Account {} activated", account.getAccountNumber());
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns accounts summary data.
     * @return the result of the operation.
     */
    public AccountSummaryResponse getAccountsSummary() {
        List<Account> all = accountRepository.findAll();
        long total    = all.size();
        long active   = all.stream().filter(Account::isActive).count();
        BigDecimal balance = all.stream()
                .filter(Account::isActive)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AccountSummaryResponse.builder()
                .totalAccounts(total)
                .activeAccounts(active)
                .inactiveAccounts(total - active)
                .totalSystemBalance(balance)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the current live savings interest rate from the interest_rates table.
     *
     * This is the same query used by SavingsInterestService and TaxServiceImpl —
     * the most recent rate whose effectiveFrom is on or before today.
     *
     * Returns null only if no rate has ever been configured (should not happen
     * after DataSeeder runs, but handled gracefully).
     */
    private BigDecimal resolveLiveSavingsRate() {
        return interestRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate.now())
                .map(InterestRate::getAnnualRate)
                .orElse(null);
    }

    /**
     * Builds an AccountResponseDto with the interest rate field substituted for
     * the live rate from interest_rates for SAVINGS accounts.
     *
     * The BANK_INTERNAL_OPERATIONS account (BOF90000001) is also a SAVINGS-type
     * account but earns no interest — its rate is kept at 0.00 and the live rate
     * is NOT applied to it. The caller (ManageAccountsPage) also enforces this
     * on the frontend, but we apply it here too for correctness.
     */
    private AccountResponseDto toResponseDtoWithLiveRate(Account account, BigDecimal liveRate) {
        BigDecimal rateToShow = account.getInterestRate();
        // Apply live rate to customer SAVINGS accounts only — not to the internal account
        boolean isCustomerSavings =
                account.getAccountType() == AccountType.SAVINGS &&
                !"BOF90000001".equals(account.getAccountNumber());
        if (isCustomerSavings && liveRate != null) {
            rateToShow = liveRate;
        }
        return AccountResponseDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .interestRate(rateToShow)
                .interestEarned(account.getInterestEarned())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    /**
     * Patches the live RBF savings rate onto an AccountResponse (used by the
     * accountMapper path for getAccountById / getAccountByNumber).
     */
    private void patchLiveRateOnResponse(AccountResponse response, Account account) {
        boolean isCustomerSavings =
                account.getAccountType() == AccountType.SAVINGS &&
                !"BOF90000001".equals(account.getAccountNumber());
        if (isCustomerSavings) {
            BigDecimal live = resolveLiveSavingsRate();
            if (live != null) response.setInterestRate(live);
        }
    }

    /**
     * Returns the static default rate for a given account type from BankingProperties.
     * Used as a fallback when no rate has been set in the interest_rates table yet.
     */
    private BigDecimal getDefaultRateForType(AccountType accountType) {
        BankingProperties.InterestRate rates = bankingProperties.getInterestRate();
        return switch (accountType) {
            case SAVINGS       -> rates.getSavings();
            case SIMPLE_ACCESS -> rates.getChecking();
        };
    }

    private String generateAccountNumber() {
        return bankingProperties.getAccountNumberPrefix()
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void validateTinForTax(Account account) {
        String tin = account.getUser() != null ? account.getUser().getTinNumber() : null;
        if (tin == null || tin.isBlank())
            throw new BadRequestException("TIN number is required before applying NRWHT tax");
    }

    private BigDecimal calculateMonthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        if (balance == null || annualRate == null) return BigDecimal.ZERO;
        return balance.multiply(annualRate).divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    private void applyNrwhtTaxToAccount(Account account) {
        BigDecimal tax = account.getInterestEarned()
                .multiply(NRWHT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        if (tax.compareTo(BigDecimal.ZERO) <= 0) return;
        if (account.getBalance().compareTo(tax) < 0)
            throw new InsufficientFundsException("Insufficient funds to apply NRWHT tax");
        account.setBalance(account.getBalance().subtract(tax));
        account.setInterestEarned(account.getInterestEarned().subtract(tax));
    }

    private User resolveAccountOwner(User requester, Long customerUserId) {
        if (customerUserId != null) {
            User selectedUser = userRepository.findById(customerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerUserId));
            if (selectedUser.getRole() != Role.CUSTOMER) {
                throw new BadRequestException("Accounts can only be created for existing customers");
            }
            if (!selectedUser.isActive()) {
                throw new BadRequestException("Cannot create account for an inactive customer");
            }
            return selectedUser;
        }

        if (requester.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Customer selection is required to create an account");
        }
        return requester;
    }

    private String normalizeAccountName(String accountName) {
        if (accountName == null) {
            return null;
        }
        String trimmed = accountName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Maintenance fee — anniversary-based (NEW) ──────────────────────────────
 
    /**
     * Charges the FJD 2.50 maintenance fee to every active SIMPLE_ACCESS account
     * whose monthly anniversary falls on {@code date} — BUT only if the account
     * was opened in a PREVIOUS month (not the current calendar month).
     *
     * WHY SKIP THE CREATION MONTH:
     *   If Toni opens her account on 31 March, charging her on 31 March itself
     *   means she pays a fee on day zero — before she has even used the account
     *   for a full month. Standard banking practice is to charge from the second
     *   month onward (the first anniversary of the creation day, which falls in
     *   April for a 31 March account).
     *
     * SKIP CONDITION:
     *   YearMonth of account.createdAt == YearMonth of date
     *   → skip (first charge will come next month)
     *
     * ANNIVERSARY LOGIC (same as before):
     *   anniversaryDay = Math.min(creationDay, lastDayOfCurrentMonth)
     *   Charge if today == anniversaryDay AND creation month != this month.
     *
     * Example — account created 31 March 2026, running on 31 March 2026:
     *   anniversaryDay = min(31, 31) = 31 → matches today
     *   BUT creation month (March) == current month (March) → SKIP ✓
     *
     * Example — account created 31 March 2026, running on 30 April 2026:
     *   anniversaryDay = min(31, 30) = 30 → matches today
     *   creation month (March) != current month (April) → CHARGE ✓
     *
     * @param date the date being checked — normally today from the scheduler
     * @return number of accounts charged
     */
    @Override
    @Transactional
    public int applyMaintenanceFeesByAnniversaryDay(LocalDate date) {
        List<Account> eligible = accountRepository
                .findByAccountTypeAndIsActiveTrue(AccountType.SIMPLE_ACCESS);
 
        int todayDay       = date.getDayOfMonth();
        int lastDayOfMonth = date.lengthOfMonth();
        YearMonth thisMonth = YearMonth.from(date);
 
        int charged      = 0;
        int skippedDay   = 0;
        int skippedFirst = 0;  // skipped because it's the creation month
        int skippedFunds = 0;
 
        for (Account account : eligible) {
            if (account.getCreatedAt() == null) {
                log.warn("Skipping account {} — createdAt is null", account.getAccountNumber());
                continue;
            }
 
            int     creationDay   = account.getCreatedAt().getDayOfMonth();
            YearMonth creationMonth = YearMonth.from(account.getCreatedAt());
 
            // ── RULE: skip the creation month entirely ────────────────────────
            // First charge only starts the month AFTER the account was opened.
            if (creationMonth.equals(thisMonth)) {
                skippedFirst++;
                log.debug("Skipping fee for {} — account created this month ({})",
                        account.getAccountNumber(), creationMonth);
                continue;
            }
 
            // ── Anniversary day (clamped to last day of short months) ─────────
            int anniversaryDay = Math.min(creationDay, lastDayOfMonth);
            if (anniversaryDay != todayDay) {
                skippedDay++;
                continue;
            }
 
            // ── Sufficient funds check ────────────────────────────────────────
            if (account.getBalance() == null
                    || account.getBalance().compareTo(MONTHLY_MAINTENANCE_FEE) < 0) {
                skippedFunds++;
                log.warn("Skipping fee for {} (creationDay={}) — insufficient funds: {}",
                        account.getAccountNumber(), creationDay, account.getBalance());
                continue;
            }
 
            account.setBalance(account.getBalance().subtract(MONTHLY_MAINTENANCE_FEE));
            charged++;
 
            log.debug("Maintenance fee charged: account={} creationDay={} date={} newBalance={}",
                    account.getAccountNumber(), creationDay, date, account.getBalance());
        }
 
        if (charged > 0) {
            // Save only the accounts we actually modified
            List<Account> toSave = eligible.stream()
                    .filter(a -> {
                        if (a.getCreatedAt() == null) return false;
                        YearMonth cm = YearMonth.from(a.getCreatedAt());
                        if (cm.equals(thisMonth)) return false;
                        int ad = Math.min(a.getCreatedAt().getDayOfMonth(), lastDayOfMonth);
                        return ad == todayDay && a.getBalance().compareTo(BigDecimal.ZERO) >= 0;
                    })
                    .collect(Collectors.toList());
            accountRepository.saveAll(toSave);
        }
 
        log.info("Anniversary fee run [{}]: charged={} skippedCreationMonth={} skippedWrongDay={} skippedFunds={}",
                date, charged, skippedFirst, skippedDay, skippedFunds);
        return charged;
    }
}
