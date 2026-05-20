package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records a single repayment transaction made against a loan.
 *
 * Each repayment is split into principal and interest components
 * using the standard amortisation formula so the outstanding
 * balance is reduced correctly.
 *
 * Balance lifecycle:
 *   ACTIVE loan → customer makes repayment → outstandingBalance decreases
 *   → if outstandingBalance <= 0 → loan status set to PAID_OFF
 */
@Entity
@Table(name = "loan_repayments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference shown to customer e.g. REP-2026-A1B2C3D4 */
    @Column(name = "reference", unique = true, nullable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    /** Account the repayment was debited from */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    /** Total amount paid by the customer */
    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    /** Portion of amountPaid applied to principal reduction */
    @Column(name = "principal_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalComponent;

    /** Portion of amountPaid covering interest */
    @Column(name = "interest_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestComponent;

    /** Outstanding loan balance AFTER this repayment was applied */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for LoanRepayment to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt   = LocalDateTime.now();
        if (paymentDate == null) paymentDate = LocalDate.now();
    }
}
