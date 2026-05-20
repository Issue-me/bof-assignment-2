package com.bof.banking.service;

import com.bof.banking.dto.account.AccountRequest;
import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.dto.account.AccountResponseDto;
import com.bof.banking.dto.account.AccountSummaryResponse;
import com.bof.banking.dto.account.CreateAccountRequestDto;
import com.bof.banking.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for bank account management operations.
 * <p>
 * Provides methods for creating, retrieving, updating, and closing
 * bank accounts. All account operations are performed within
 * transactional boundaries.
 * </p>
 *
 * @author Bank of Fiji Development Team
 * @version 1.0.0
 */
public interface AccountService {

    /**
     * Creates an account using the minimal account creation contract.
     *
     * @param userEmail the email of the account owner
     * @param request   account creation payload
     * @return the created account details
     */
    AccountResponseDto createAccount(String userEmail, CreateAccountRequestDto request);

    /**
     * Creates a new bank account for the specified user.
     *
     * @param userEmail the email of the user who will own the account
     * @param request   the account creation request containing account details
     * @return the created account details
     * @throws ResourceNotFoundException if the user is not found
     */
    AccountResponse createAccount(String userEmail, AccountRequest request);

    /**
     * Retrieves an account by its unique identifier.
     *
     * @param id the unique identifier of the account
     * @return the account details
     * @throws ResourceNotFoundException if the account is not found
     */
    AccountResponse getAccountById(Long id);

    /**
     * Retrieves a specific account that belongs to the authenticated user.
     *
     * @param userEmail authenticated user's email
     * @param id        account id
     * @return account details
     */
    AccountResponseDto getAccountById(String userEmail, Long id);

    /**
     * Retrieves an account by its account number.
     *
     * @param accountNumber the unique account number
     * @return the account details
     * @throws ResourceNotFoundException if the account is not found
     */
    AccountResponse getAccountByNumber(String accountNumber);

    /**
     * Retrieves all accounts belonging to a user.
     *
     * @param userEmail the email of the account owner
     * @return list of all accounts (active and inactive) belonging to the user
     * @throws ResourceNotFoundException if the user is not found
     */
    List<AccountResponseDto> getAccountsByUser(String userEmail);

    /**
     * Retrieves all accounts in the system.
     * Intended for TELLER and ADMIN use.
     */
    List<AccountResponseDto> getAllAccounts();

    /**
     * Calculates monthly savings interest and applies NRWHT tax.
     *
     * @param userEmail authenticated user's email
     * @param accountId account id
     * @return updated account details
     */
    AccountResponseDto calculateSavingsInterest(String userEmail, Long accountId);

    /**
     * Applies 10% NRWHT tax to accrued interest for savings accounts.
     *
     * @param userEmail authenticated user's email
     * @param accountId account id
     * @return updated account details
     */
    AccountResponseDto applyNRWHTTax(String userEmail, Long accountId);

    /**
     * Backward-compatible alias; delegates to calculateSavingsInterest.
     */
    @Deprecated
    AccountResponseDto calculateInterest(String userEmail, Long accountId);

    /**
     * Applies monthly maintenance fee for eligible account types.
     *
     * @param userEmail authenticated user's email
     * @param accountId account id
     * @return updated account details
     */
    AccountResponseDto applyMonthlyMaintenanceFee(String userEmail, Long accountId);

    /**
     * Applies monthly maintenance fees to all eligible active SIMPLE_ACCESS accounts.
     *
     * @return number of accounts successfully charged
     */
    int applyMonthlyMaintenanceFeesForAllEligibleAccounts();

    /**
     * Retrieves only active accounts belonging to a user.
     *
     * @param userEmail the email of the account owner
     * @return list of active accounts belonging to the user
     * @throws ResourceNotFoundException if the user is not found
     */
    List<AccountResponse> getActiveAccountsByUser(String userEmail);

    /**
     * Updates an existing account's details.
     *
     * @param id      the unique identifier of the account to update
     * @param request the account update request
     * @return the updated account details
     * @throws ResourceNotFoundException if the account is not found
     */
    AccountResponse updateAccount(Long id, AccountRequest request);

    /**
     * Closes an account (soft delete).
     * <p>
     * The account is not physically deleted but marked as inactive.
     * </p>
     *
     * @param id the unique identifier of the account to close
     * @throws ResourceNotFoundException if the account is not found
     */
    void closeAccount(Long id);

    /**
     * Activates a previously closed account.
     * <p>
     * Reactivates an account that was previously marked as inactive.
     * </p>
     *
     * @param id the unique identifier of the account to activate
     * @throws ResourceNotFoundException if the account is not found
     */
    void activateAccount(Long id);

    /**
     * Retrieves system-wide account summary statistics.
     * <p>
     * Provides overview metrics including total accounts, active/inactive counts,
     * and total system balance. Intended for TELLER and ADMIN dashboard use.
     * </p>
     *
     * @return account summary statistics
     */
    com.bof.banking.dto.account.AccountSummaryResponse getAccountsSummary();

    /**
     * NEW — charges only the SIMPLE_ACCESS accounts whose monthly anniversary
     * falls on the given date.
     *
     * "Anniversary day" = the day-of-month the account was created (createdAt).
     * If a month is shorter than that day (e.g. account created on 31st, month
     * is February), the account is charged on the last day of that month instead.
     *
     * Called daily by MaintenanceFeeScheduler, passing LocalDate.now().
     * Can also be called manually by an admin endpoint for a specific date.
     *
     * @param date the date to check — typically today
     * @return number of accounts successfully charged
     */
    int applyMaintenanceFeesByAnniversaryDay(LocalDate date);
}
