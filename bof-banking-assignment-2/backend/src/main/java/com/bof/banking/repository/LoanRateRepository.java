package com.bof.banking.repository;

import com.bof.banking.model.LoanRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * Defines persistence operations used to query and store l oa nr at er ep os it or y data.
 */
public interface LoanRateRepository extends JpaRepository<LoanRate, Long> {

    Optional<LoanRate> findByLoanType(String loanType);

    List<LoanRate> findAllByOrderByLoanTypeAsc();
}
