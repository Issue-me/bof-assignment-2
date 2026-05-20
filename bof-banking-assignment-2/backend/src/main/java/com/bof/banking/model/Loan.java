// package com.bof.banking.model;

// import com.bof.banking.model.enums.LoanStatus;
// import jakarta.persistence.*;
// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.time.LocalDateTime;

// /**
//  * Represents a loan account.
//  */
// @Entity
// @Table(name = "loans")
// @Data
// @Builder
// @NoArgsConstructor
// @AllArgsConstructor
// public class Loan {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     @Column(name = "loan_number", unique = true, nullable = false)
//     private String loanNumber;

//     @Column(name = "loan_type", nullable = false)
//     private String loanType;

//     @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
//     private BigDecimal principalAmount;

//     @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
//     private BigDecimal interestRate;

//     @Column(name = "term_months", nullable = false)
//     private Integer termMonths;

//     @Column(name = "monthly_payment", precision = 19, scale = 2)
//     private BigDecimal monthlyPayment;

//     @Column(name = "outstanding_balance", precision = 19, scale = 2)
//     private BigDecimal outstandingBalance;

//     @Enumerated(EnumType.STRING)
//     @Column(nullable = false)
//     @Builder.Default
//     private LoanStatus status = LoanStatus.PENDING;

//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "user_id", nullable = false)
//     private User user;

//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "disbursement_account_id")
//     private Account disbursementAccount;

//     @Column(name = "application_date", nullable = false)
//     private LocalDate applicationDate;

//     @Column(name = "approval_date")
//     private LocalDate approvalDate;

//     @Column(name = "start_date")
//     private LocalDate startDate;

//     @Column(name = "end_date")
//     private LocalDate endDate;

//     @Column(name = "created_at", nullable = false, updatable = false)
//     private LocalDateTime createdAt;

//     @Column(name = "updated_at")
//     private LocalDateTime updatedAt;

//     @Column(name = "rejection_reason")
//     private String rejectionReason;

//     @PrePersist
//     protected void onCreate() {
//         createdAt = LocalDateTime.now();
//         updatedAt = LocalDateTime.now();
//         if (applicationDate == null) {
//             applicationDate = LocalDate.now();
//         }
//     }

//     @PreUpdate
//     protected void onUpdate() {
//         updatedAt = LocalDateTime.now();
//     }
// }


package com.bof.banking.model;

import com.bof.banking.model.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a loan account.
 *
 * Added fields:
 *   - purpose           — loan purpose (Education, Medical, etc.)
 *   - employmentType    — needed for admin review
 *   - monthlyIncome     — stored for DSR audit trail
 */
@Entity
@Table(name = "loans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_number", unique = true, nullable = false)
    private String loanNumber;

    @Column(name = "loan_type", nullable = false)
    private String loanType;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_payment", precision = 19, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "outstanding_balance", precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_account_id")
    private Account disbursementAccount;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "monthly_income", precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @PrePersist
    /**
     * Hooks into lifecycle processing for Loan to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (applicationDate == null) applicationDate = LocalDate.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for Loan to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
