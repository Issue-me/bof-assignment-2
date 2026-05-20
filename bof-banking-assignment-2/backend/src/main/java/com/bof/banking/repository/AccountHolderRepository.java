package com.bof.banking.repository;

import com.bof.banking.model.AccountHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing account holder relationships.
 */
@Repository
public interface AccountHolderRepository extends JpaRepository<AccountHolder, Long> {
    
    /**
     * Find all account holders for a specific account.
     */
    List<AccountHolder> findByAccountId(Long accountId);
    
    /**
     * Find all accounts where a user is an account holder.
     */
    List<AccountHolder> findByUserId(Long userId);
    
    /**
     * Check if a user is an account holder for a specific account.
     */
    boolean existsByAccountIdAndUserId(Long accountId, Long userId);
    
    /**
     * Find a specific account holder relationship.
     */
    Optional<AccountHolder> findByAccountIdAndUserId(Long accountId, Long userId);
    
    /**
     * Delete an account holder relationship.
     */
    void deleteByAccountIdAndUserId(Long accountId, Long userId);
}
