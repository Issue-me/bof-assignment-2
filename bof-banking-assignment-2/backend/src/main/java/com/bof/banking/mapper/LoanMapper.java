
package com.bof.banking.mapper;

import com.bof.banking.dto.loan.LoanDocumentResponse;
import com.bof.banking.dto.loan.LoanResponse;
import com.bof.banking.model.Loan;
import com.bof.banking.model.LoanDocument;
import com.bof.banking.repository.LoanDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps Loan entity → LoanResponse DTO.
 *
 * Includes document metadata in every response so the frontend can
 * display the document list without a separate API call.
 */
@Component
@RequiredArgsConstructor
public class LoanMapper {

    private final LoanDocumentRepository documentRepository;

    /** Maps without a DSR value — use for list/get endpoints */
    /**
     * Handles to response.
     * @param loan the loan.
     * @return the result of the operation.
     */
    public LoanResponse toResponse(Loan loan) {
        return toResponse(loan, null);
    }

    /**
     * Maps with an optional DSR value.
     * Use immediately after {@code apply()} where the DSR has just been computed.
     */
    public LoanResponse toResponse(Loan loan, BigDecimal dsr) {
        if (loan == null) return null;

        List<LoanDocument> docs = Collections.emptyList();
        try {
            docs = documentRepository.findByLoanOrderByUploadedAtDesc(loan);
        } catch (Exception ignored) {
            // Safe fallback if document table doesn't exist yet
        }

        List<LoanDocumentResponse> docResponses = docs.stream()
                .map(this::toDocResponse)
                .collect(Collectors.toList());

        return LoanResponse.builder()
                .id(loan.getId())
                .loanNumber(loan.getLoanNumber())
                .loanType(loan.getLoanType())
                .purpose(loan.getPurpose())
                .principalAmount(loan.getPrincipalAmount())
                .outstandingBalance(loan.getOutstandingBalance())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .monthlyPayment(loan.getMonthlyPayment())
                .status(loan.getStatus().name())
                .disbursementAccountNumber(
                        loan.getDisbursementAccount() != null
                                ? loan.getDisbursementAccount().getAccountNumber()
                                : null)
                .employmentType(loan.getEmploymentType())
                .monthlyIncome(loan.getMonthlyIncome())
                .rejectionReason(loan.getRejectionReason())
                .debtServiceRatio(dsr)
                .applicationDate(loan.getApplicationDate())
                .approvalDate(loan.getApprovalDate())
                .startDate(loan.getStartDate())
                .endDate(loan.getEndDate())
                .customerFullName(loan.getUser() != null ? loan.getUser().getFullName() : null)
                .customerId(loan.getUser() != null ? loan.getUser().getCustomerId() : null)
                .documents(docResponses)
                .hasDocuments(!docResponses.isEmpty())
                .build();
    }

    private LoanDocumentResponse toDocResponse(LoanDocument d) {
        return LoanDocumentResponse.builder()
                .id(d.getId())
                .loanId(d.getLoan() != null ? d.getLoan().getId() : null)
                .documentType(d.getDocumentType())
                .fileName(d.getFileName())
                .contentType(d.getContentType())
                .fileSize(d.getFileSize())
                .uploadedBy(d.getUploadedBy())
                .uploadedAt(d.getUploadedAt())
                .build();
    }
}
