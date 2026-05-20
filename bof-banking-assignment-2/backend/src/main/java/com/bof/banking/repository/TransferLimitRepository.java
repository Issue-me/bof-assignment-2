package com.bof.banking.repository;

import com.bof.banking.model.TransferLimit;
import com.bof.banking.model.enums.TransferLimitCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for transfer limit configuration.
 */
@Repository
public interface TransferLimitRepository extends JpaRepository<TransferLimit, Long> {

    Optional<TransferLimit> findByCategory(TransferLimitCategory category);
}
