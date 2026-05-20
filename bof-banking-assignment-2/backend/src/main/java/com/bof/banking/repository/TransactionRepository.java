package com.bof.banking.repository;

import com.bof.banking.model.Account;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA repository for Transaction entity.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    List<Transaction> findBySourceAccountOrDestinationAccountOrderByTransactionDateDesc(
            Account sourceAccount, Account destinationAccount);

    Page<Transaction> findBySourceAccountOrDestinationAccount(
            Account sourceAccount, Account destinationAccount, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount = :account OR t.destinationAccount = :account) " +
            "AND t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountAndDateRange(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount IN :accounts OR t.destinationAccount IN :accounts) " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentTransactionsForAccounts(
            @Param("accounts") List<Account> accounts,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount = :account OR t.destinationAccount = :account) " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentTransactionsForAccount(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.destinationAccount IN :accounts " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentCreditsForAccounts(
            @Param("accounts") List<Account> accounts,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount IN :accounts " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentDebitsForAccounts(
            @Param("accounts") List<Account> accounts,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.destinationAccount = :account " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentCreditsForAccount(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount = :account " +
            "AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    Page<Transaction> findRecentDebitsForAccount(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);

    @Query("""
            SELECT SUM(t.amount)
            FROM Transaction t
            LEFT JOIN t.destinationAccount da
            LEFT JOIN da.user du
            WHERE t.sourceAccount.user.id = :userId
                                                        AND t.transactionDate >= :startDate
              AND t.transactionType = :transactionType
              AND t.status = :status
              AND du.id = :userId
            """)
    BigDecimal sumCompletedOwnTransfersSince(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("transactionType") TransactionType transactionType,
            @Param("status") PaymentStatus status);

    @Query("""
            SELECT SUM(t.amount)
            FROM Transaction t
            LEFT JOIN t.destinationAccount da
            LEFT JOIN da.user du
            WHERE t.sourceAccount.user.id = :userId
                                                        AND t.transactionDate >= :startDate
              AND t.transactionType = :transactionType
              AND t.status = :status
              AND (du.id IS NULL OR du.id <> :userId)
            """)
    BigDecimal sumCompletedExternalTransfersSince(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("transactionType") TransactionType transactionType,
            @Param("status") PaymentStatus status);

    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.sourceAccount.id IN :accountIds " +
           "    OR t.destinationAccount.id IN :accountIds) " +
            "AND t.transactionDate BETWEEN :from AND :to " +
            "ORDER BY t.transactionDate ASC")
    List<Transaction> findByUserAccountsAndDateRange(
            @Param("accountIds") Set<Long> accountIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

   @Query(value = """
           SELECT t.*
           FROM transactions t
           LEFT JOIN accounts sa ON sa.id = t.source_account_id
           LEFT JOIN accounts da ON da.id = t.destination_account_id
                 WHERE (CAST(:search AS TEXT) IS NULL
                        OR CAST(t.reference_number AS TEXT) ILIKE :search
                        OR CAST(COALESCE(t.description, '') AS TEXT) ILIKE :search
                        OR CAST(COALESCE(sa.account_number, '') AS TEXT) ILIKE :search
                        OR CAST(COALESCE(da.account_number, '') AS TEXT) ILIKE :search)
                         AND (CAST(:transactionType AS TEXT) IS NULL OR t.transaction_type = CAST(:transactionType AS TEXT))
                         AND (CAST(:status AS TEXT) IS NULL OR t.status = CAST(:status AS TEXT))
                         AND (CAST(:from AS TIMESTAMP) IS NULL OR t.transaction_date >= CAST(:from AS TIMESTAMP))
                         AND (CAST(:to AS TIMESTAMP) IS NULL OR t.transaction_date <= CAST(:to AS TIMESTAMP))
           """,
           countQuery = """
           SELECT COUNT(*)
           FROM transactions t
           LEFT JOIN accounts sa ON sa.id = t.source_account_id
           LEFT JOIN accounts da ON da.id = t.destination_account_id
                 WHERE (CAST(:search AS TEXT) IS NULL
                        OR CAST(t.reference_number AS TEXT) ILIKE :search
                        OR CAST(COALESCE(t.description, '') AS TEXT) ILIKE :search
                        OR CAST(COALESCE(sa.account_number, '') AS TEXT) ILIKE :search
                        OR CAST(COALESCE(da.account_number, '') AS TEXT) ILIKE :search)
                         AND (CAST(:transactionType AS TEXT) IS NULL OR t.transaction_type = CAST(:transactionType AS TEXT))
                         AND (CAST(:status AS TEXT) IS NULL OR t.status = CAST(:status AS TEXT))
                         AND (CAST(:from AS TIMESTAMP) IS NULL OR t.transaction_date >= CAST(:from AS TIMESTAMP))
                         AND (CAST(:to AS TIMESTAMP) IS NULL OR t.transaction_date <= CAST(:to AS TIMESTAMP))
           """,
           nativeQuery = true)
   Page<Transaction> searchForMonitoring(
                 @Param("search") String search,
                 @Param("transactionType") String transactionType,
                 @Param("status") String status,
                 @Param("from") LocalDateTime from,
                 @Param("to") LocalDateTime to,
                 Pageable pageable);

    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND t.transactionDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY t.transactionDate ASC")
    List<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND t.transactionDate BETWEEN :fromDate AND :toDate")
    long countByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND t.transactionDate < :beforeDate " +
           "ORDER BY t.transactionDate ASC")
    List<Transaction> findTransactionsBefore(
            @Param("accountId") Long accountId,
            @Param("beforeDate") LocalDateTime beforeDate);

    // ══════════════════════════════════════════════════════════════════════════
    // TAX REPORT QUERIES — added for TaxServiceImpl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sum of INTEREST transactions credited to SAVINGS accounts owned by the user
     * within a date range. Used to calculate gross interest earned for tax reports.
     *
     * Joins through destinationAccount → user so we only capture credits TO the
     * user's own savings accounts (not debits from them).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.destinationAccount.user = :user " +
           "AND t.destinationAccount.accountType = :accountType " +
           "AND t.transactionType = :transactionType " +
           "AND t.transactionDate BETWEEN :from AND :to")
    Optional<BigDecimal> sumAmountByUserAndTypeAndAccountTypeAndDateRange(
            @Param("user") User user,
            @Param("transactionType") TransactionType transactionType,
            @Param("accountType") AccountType accountType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Sum of all credits (money arriving into any of the user's accounts)
     * within a date range. Used to calculate gross income for tax reports.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.destinationAccount.user = :user " +
           "AND t.transactionDate BETWEEN :from AND :to")
    Optional<BigDecimal> sumCreditsByUserAndDateRange(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Sum of all debits (money leaving any of the user's accounts)
     * within a date range. Used to calculate total debits for tax reports.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.sourceAccount.user = :user " +
           "AND t.transactionDate BETWEEN :from AND :to")
    Optional<BigDecimal> sumDebitsByUserAndDateRange(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Count of all transactions involving any of the user's accounts
     * within a date range. Used for the transaction count on tax reports.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE (t.sourceAccount.user = :user OR t.destinationAccount.user = :user) " +
           "AND t.transactionDate BETWEEN :from AND :to")
    long countByUserAndDateRange(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Sum of FEE transactions charged to any of the user's accounts
     * within a date range. Used to calculate VAT on bank fees for tax reports.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.sourceAccount.user = :user " +
           "AND t.transactionType = com.bof.banking.model.enums.TransactionType.FEE " +
           "AND t.transactionDate BETWEEN :from AND :to")
    Optional<BigDecimal> sumFeesByUserAndDateRange(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

        @Query("""
        SELECT t FROM Transaction t
        WHERE t.destinationAccount = :account
        AND t.transactionType = com.bof.banking.model.enums.TransactionType.DEPOSIT
        AND t.description LIKE 'NRWHT Refund%'
        AND YEAR(t.transactionDate) = :year
        ORDER BY t.transactionDate DESC
        """)
        List<Transaction> findNrwhtRefundsByAccountAndYear(
                @Param("account") Account account,
                @Param("year")    int year);
}