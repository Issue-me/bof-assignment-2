// package com.bof.banking.dto.loan;

// import com.fasterxml.jackson.annotation.JsonProperty;
// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// /**
//  * Response DTO returned for loan queries.
//  */
// @Data
// @Builder
// @NoArgsConstructor
// @AllArgsConstructor
// public class LoanResponse {

//     private Long       id;
//     private String     loanNumber;
//     private String     loanType;
//     private String     purpose;
//     private String     status;            // PENDING, APPROVED, ACTIVE, REJECTED, CLOSED

//     private BigDecimal principalAmount;
//     private BigDecimal outstandingBalance;
//     private BigDecimal interestRate;      // e.g. 0.085 = 8.5%
//     private Integer    termMonths;
//     private BigDecimal monthlyPayment;

//     private String     disbursementAccountNumber;
//     private String     employmentType;
//     private BigDecimal monthlyIncome;

//     // DSR calculated at application time
//     private BigDecimal debtServiceRatio;

//     // Rejection reason shown to customer
//     private String     rejectionReason;

//     private LocalDate  applicationDate;
//     private LocalDate  approvalDate;
//     private LocalDate  startDate;
//     private LocalDate  endDate;

//     // Applicant info (populated for admin view)
//     private String     customerFullName;
//     private String     customerId;
// }

package com.bof.banking.dto.loan;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO returned for loan queries.
 *
 * Added fields:
 *   - purpose, employmentType, monthlyIncome   — needed by admin review panel
 *   - documents                                 — list of uploaded document metadata
 *   - hasDocuments                              — quick flag for UI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {

    private Long       id;
    private String     loanNumber;
    private String     loanType;
    private String     purpose;
    private String     status;           // PENDING, ACTIVE, REJECTED, CLOSED, PAID_OFF

    private BigDecimal principalAmount;
    private BigDecimal outstandingBalance;
    private BigDecimal interestRate;     // e.g. 0.085 = 8.5%
    private Integer    termMonths;
    private BigDecimal monthlyPayment;

    private String     disbursementAccountNumber;
    private String     employmentType;
    private BigDecimal monthlyIncome;

    private BigDecimal debtServiceRatio;
    private String     rejectionReason;

    private LocalDate  applicationDate;
    private LocalDate  approvalDate;
    private LocalDate  startDate;
    private LocalDate  endDate;

    // Applicant info (populated for admin view)
    private String     customerFullName;
    private String     customerId;

    // Documents attached to this application
    private List<LoanDocumentResponse> documents;
    private boolean    hasDocuments;
}