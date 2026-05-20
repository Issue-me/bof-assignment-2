package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persisted record of a user's annual interest summary.
 *
 * <p>One row per (user, taxYear). Computed by
 * {@link com.bof.banking.service.impl.InterestSummaryCalculator} and stored so
 * the FRCS end-of-year report can be regenerated without re-scanning transactions.
 *
 * <p><b>Withholding rules:</b>
 * <ul>
 *   <li>Senior citizen → no withholding, {@code exemptionReason = SENIOR_CITIZEN_EXEMPTION}</li>
 *   <li>Non-resident OR no TIN → NRWHT at 10% of gross interest</li>
 *   <li>Resident with TIN → RIWT at 10% of gross interest</li>
 * </ul>
 *
 * <p>{@code @ToString(exclude = "user")} prevents StackOverflowError from the
 * lazy-loaded {@code @ManyToOne} being traversed in toString().
 */
@Entity
@Table(
    name = "user_interest_summaries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_user_interest_year",
        columnNames = {"user_id", "tax_year"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "user")
@EqualsAndHashCode(exclude = "user")
/**
 * Persisted record of a user's annual interest summary. One row per (user, taxYear). Computed by InterestSummaryCalculator and stored so the FRCS end-of-year report can be regenerated without re-scanning transactions. Withholding rules: Senior citizen → no withholding, exemptionReason = SENIOR_CITIZEN_EXEMPTION Non-resident OR no TIN → NRWHT at 10% of gross interest Resident with TIN → RIWT at 10% of gross interest @ToString(exclude = "user") prevents StackOverflowError from the lazy-loaded @ManyToOne being traversed in toString().
 */
public class UserInterestSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── User reference ────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    // ── Interest figures ──────────────────────────────────────────────────
    @Column(name = "gross_interest_earned", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal grossInterestEarned = BigDecimal.ZERO;

    /** NRWHT withheld (10% of gross). Non-zero for non-residents or users without TIN. */
    @Column(name = "nrwht_withheld", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal nrwhtWithheld = BigDecimal.ZERO;

    /** RIWT withheld (10% of gross). Non-zero for resident users with a TIN. */
    @Column(name = "riwt_withheld", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal riwtWithheld = BigDecimal.ZERO;

    /** Net interest paid = gross - nrwht - riwt. */
    @Column(name = "net_interest_paid", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal netInterestPaid = BigDecimal.ZERO;

    // ── Exemption ─────────────────────────────────────────────────────────
    /** e.g. "SENIOR_CITIZEN_EXEMPTION". Null when withholding applies. */
    @Column(name = "exemption_reason", length = 100)
    private String exemptionReason;

    // ── Audit ─────────────────────────────────────────────────────────────
    @Column(name = "interest_transaction_count", nullable = false)
    @Builder.Default
    private int interestTransactionCount = 0;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for UserInterestSummary to keep entity state consistent.
     */
    protected void onCreate() {
        calculatedAt = LocalDateTime.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for UserInterestSummary to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Column(name = "frcs_reference")
    private String frcsReference;

    @Column(name = "submitted_to_frcs")
    private boolean submittedToFrcs = false;

    @Column(name = "frcs_submission_date")
    private LocalDate frcsSubmissionDate;

    @Column(name = "customer_submitted")
    private boolean customerSubmitted = false;

    @Column(name = "customer_submitted_date")
    private LocalDate customerSubmittedDate;

    /**
     * True when NRWHT deducted earlier in the year has been refunded
     * after TIN registration.
     */
    @Column(name = "nrwht_refunded", nullable = false)
    @Builder.Default
    private boolean nrwhtRefunded = false;

    /**
     * Reference number for the refund transaction, e.g.
     * NRWHT-REFUND-2026-BOF33445566.
     */
    @Column(name = "nrwht_refund_reference", length = 100)
    private String nrwhtRefundReference;
}
