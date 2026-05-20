package com.bof.banking.repository;

import com.bof.banking.model.Loan;
import com.bof.banking.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
 * Count documents by type for a loan — used for validation
 */
public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Long> {

    /** All documents uploaded for a specific loan, newest first */
    List<LoanDocument> findByLoanOrderByUploadedAtDesc(Loan loan);

    /** Count documents by type for a loan — used for validation */
    long countByLoanAndDocumentType(Loan loan, String documentType);

    /** Check if a document belongs to a specific loan */
    boolean existsByIdAndLoan(Long id, Loan loan);
}
