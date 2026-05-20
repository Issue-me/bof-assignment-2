package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records every monthly interest credit event for a customer savings account.
 *
 * This gives full auditability — each row shows:
 *   - which account received interest
 *   - how much was credited
 *   - what balance and rate were used
 *   - which period it covers (interestMonth / interestYear)
 *   - whether RIWT was deducted
 *
 * Used by TaxReportService to calculate interestEarned per year,
 * and shown in AccountsPage as interest history.
 */
@Entity
@Table(
    name = "savings_interest_transactions",
    indexes = {
        @Index(name = "idx_sit_account_id",   columnList = "account_id"),
        @Index(name = "idx_sit_period",       columnList = "interest_year, interest_month"),
        @Index(name = "idx_sit_credited_at",  columnList = "credited_at")
    },
    uniqueConstraints = {
        // Prevent double-crediting the same account in the same month/year
        @UniqueConstraint(
            name  = "uq_sit_account_period",
            columnNames = {"account_id", "interest_month", "interest_year"}
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Records every monthly interest credit event for a customer savings account. This gives full auditability — each row shows: - which account received interest - how much was credited - what balance and rate were used - which period it covers (interestMonth / interestYear) - whether RIWT was deducted Used by TaxReportService to calculate interestEarned per year, and shown in AccountsPage as interest history.
 */
public class SavingsInterestTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer savings account that received the interest */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** Balance used for interest calculation (snapshot at run time) */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceSnapshot;

    /** Annual interest rate at time of calculation, e.g. 0.025 = 2.5% */
    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal annualRate;

    /** Gross interest credited = balanceSnapshot × (annualRate / 12) */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossInterest;

    /**
     * RIWT deducted (Resident Interest Withholding Tax).
     * Standard rate: 10% of gross interest.
     * Zero if the customer holds an approved RIWT exemption for this year.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal riwtDeducted;

    /** Net interest actually credited to customer = grossInterest − riwtDeducted */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netInterest;

    /** Calendar month 1–12 this credit covers */
    @Column(nullable = false)
    private int interestMonth;

    /** Calendar year this credit covers */
    @Column(nullable = false)
    private int interestYear;

    /** Whether the customer had an approved RIWT exemption when this ran */
    @Column(nullable = false)
    private boolean riwtExempt;

    /** When the credit was actually applied */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime creditedAt;

    /** Reference number shown in transaction history, e.g. INT-2026-03-BOF12345678 */
    @Column(nullable = false, length = 60)
    private String referenceNumber;
}
