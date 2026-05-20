package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores the current interest rate for each loan product type.
 *
 * One row per loan type. Admin/tellers update these via the Loan Rate
 * Manager and new loan applications automatically pick up the current rate.
 *
 * Loan types:
 *   PERSONAL_LOAN, HOME_LOAN, VEHICLE_LOAN, BUSINESS_LOAN
 */
@Entity
@Table(name = "loan_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Loan type key — matches the loanType string used in applications */
    @Column(name = "loan_type", unique = true, nullable = false, length = 50)
    private String loanType;

    /** Annual rate as decimal e.g. 0.085000 = 8.5% */
    @Column(name = "annual_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal annualRate;

    /** Who last changed this rate */
    @Column(name = "set_by")
    private String setBy;

    /** Optional reason / RBF reference for the change */
    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    /**
     * Hooks into lifecycle processing for LoanRate to keep entity state consistent.
     */
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
