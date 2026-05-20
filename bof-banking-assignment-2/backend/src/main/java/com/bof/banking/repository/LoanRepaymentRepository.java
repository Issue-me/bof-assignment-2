package com.bof.banking.repository;

import com.bof.banking.model.Loan;
import com.bof.banking.model.LoanRepayment;
import com.bof.banking.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * All repayments for a loan, newest first
 */
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {

    /** All repayments for a loan, newest first */
    List<LoanRepayment> findByLoanOrderByPaymentDateDesc(Loan loan);

    /**
     * Fetch all repayments for a given loan, with sourceAccount
     * eagerly joined in a single query to avoid N+1 and lazy-load errors.
     * Results ordered newest first.
     */
    @Query("SELECT r FROM LoanRepayment r " +
           "JOIN FETCH r.sourceAccount " +
           "WHERE r.loan = :loan " +
           "ORDER BY r.paymentDate DESC, r.id DESC")
    List<LoanRepayment> findByLoanWithAccount(@Param("loan") Loan loan);
}
