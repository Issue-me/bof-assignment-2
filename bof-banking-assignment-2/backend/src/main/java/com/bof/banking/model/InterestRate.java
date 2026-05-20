package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stores the Bank of Fiji savings interest rate ladder.
 *
 * Each row represents one rate period. Only the effectiveFrom date is stored;
 * effectiveTo is derived at query time as (next row's effectiveFrom - 1 day).
 *
 * Status logic (derived, not stored):
 *   SCHEDULED  — effectiveFrom > today
 *   ACTIVE     — effectiveFrom <= today AND no later row exists with effectiveFrom <= today
 *   SUPERSEDED — effectiveFrom < today AND a later ACTIVE/SCHEDULED row exists
 *
 * The currently active rate is always the row with the largest effectiveFrom
 * that is still <= today.
 */
@Entity
@Table(
    name = "interest_rates",
    indexes = {
        @Index(name = "idx_ir_effective_from", columnList = "effective_from DESC")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Stores the Bank of Fiji savings interest rate ladder. Each row represents one rate period. Only the effectiveFrom date is stored; effectiveTo is derived at query time as (next row's effectiveFrom - 1 day). Status logic (derived, not stored): SCHEDULED — effectiveFrom > today ACTIVE — effectiveFrom invalid input: '<' = today AND no later row exists with effectiveFrom invalid input: '<' = today SUPERSEDED — effectiveFrom invalid input: '<' today AND a later ACTIVE/SCHEDULED row exists The currently active rate is always the row with the largest effectiveFrom that is still invalid input: '<' = today.
 */
public class InterestRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Annual rate as a decimal fraction, e.g. 0.025 = 2.5%.
     * Stored as decimal so calculations are direct: balance × annualRate / 12.
     */
    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal annualRate;

    /** Date from which this rate applies (inclusive). */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** Optional RBF directive reference or reason for change. */
    @Column(length = 500)
    private String changeReason;

    /** Email of the teller/admin who set this rate. */
    @Column(nullable = false, length = 255)
    private String setBy;

    /** When this record was created in the system. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
