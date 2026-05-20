package com.bof.banking.repository;

import com.bof.banking.model.Account;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountType;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * All accounts of a given type (enum) — used for interest rate broadcast
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUser(User user);

    List<Account> findByUserId(Long userId);

    List<Account> findByUserAndIsActiveTrue(User user);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIdAndUser_Email(Long id, String email);

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<Account> findByIdsForUpdate(@Param("ids") List<Long> ids);

    /** All accounts of a given type (enum) — used for interest rate broadcast */
    List<Account> findByAccountType(AccountType accountType);

    /**
     * All ACTIVE accounts of a given type.
     * Used by SavingsInterestService to load all active SAVINGS accounts.
     */
    List<Account> findByAccountTypeAndIsActiveTrue(AccountType accountType);

    /** All accounts owned by a user of a specific type */
    List<Account> findByUserAndAccountType(User user, AccountType accountType);

    /** Distinct users who own an active account of a given type — used for broadcast notifications */
    @Query("""
        SELECT DISTINCT a.user FROM Account a
        WHERE a.accountType = :accountType
          AND a.isActive = true
          AND a.user IS NOT NULL
    """)
    List<User> findDistinctUsersByAccountType(@Param("accountType") AccountType accountType);
}
