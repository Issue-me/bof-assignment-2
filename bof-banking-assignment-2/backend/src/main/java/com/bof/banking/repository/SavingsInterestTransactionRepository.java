package com.bof.banking.repository;

import com.bof.banking.model.Account;
import com.bof.banking.model.SavingsInterestTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
/**
 * Check whether a particular account has already been credited for a month/year
 */
public interface SavingsInterestTransactionRepository
        extends JpaRepository<SavingsInterestTransaction, Long> {

    /** All credits for one account, newest first */
    List<SavingsInterestTransaction> findByAccountOrderByCreditedAtDesc(Account account);

    /** All credits for one account in a specific tax year, newest first */
    List<SavingsInterestTransaction> findByAccountAndInterestYearOrderByCreditedAtDesc(
            Account account, int interestYear);

    /** Check whether a particular account has already been credited for a month/year */
    boolean existsByAccountAndInterestMonthAndInterestYear(
            Account account, int month, int year);

    /** Total GROSS interest earned by an account in a tax year (for tax report) */
    @Query("""
        SELECT COALESCE(SUM(s.grossInterest), 0)
        FROM SavingsInterestTransaction s
        WHERE s.account = :account AND s.interestYear = :year
    """)
    BigDecimal sumGrossInterestByAccountAndYear(
            @Param("account") Account account,
            @Param("year")    int year);

    /** Total NET interest credited (after RIWT) by account and year */
    @Query("""
        SELECT COALESCE(SUM(s.netInterest), 0)
        FROM SavingsInterestTransaction s
        WHERE s.account = :account AND s.interestYear = :year
    """)
    BigDecimal sumNetInterestByAccountAndYear(
            @Param("account") Account account,
            @Param("year")    int year);

    /** Total RIWT withheld by account and year */
    @Query("""
        SELECT COALESCE(SUM(s.riwtDeducted), 0)
        FROM SavingsInterestTransaction s
        WHERE s.account = :account AND s.interestYear = :year
    """)
    BigDecimal sumRiwtByAccountAndYear(
            @Param("account") Account account,
            @Param("year")    int year);

    /** All credits for a given month/year (for admin run-report) */
    List<SavingsInterestTransaction> findByInterestMonthAndInterestYear(int month, int year);

    /** Most recent credit for an account (to show "last interest paid" date) */
    Optional<SavingsInterestTransaction> findTopByAccountOrderByCreditedAtDesc(Account account);
}
