package com.bof.banking.model;

import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.ScheduleFrequency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a scheduled bill payment that will be executed automatically
 * or manually on specified frequencies.
 */
@Entity
@Table(name = "scheduled_bill_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledBillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @Column(name = "bill_reference", nullable = false)
    private String billReference; // Customer's bill account number at the biller

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private ScheduleFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate; // Optional: null means indefinite

    @Column(name = "next_execution_date")
    private LocalDate nextExecutionDate; // Calculated for scheduler

    @Column(name = "auto_pay_enabled", nullable = false)
    @Builder.Default
    private boolean autoPayEnabled = true;

    @Column(name = "approval_given", nullable = false)
    @Builder.Default
    private boolean approvalGiven = true;

    @Column(name = "last_processed_month")
    private Integer lastProcessedMonth;

    @Column(name = "last_processed_year")
    private Integer lastProcessedYear;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.ACTIVE;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for ScheduledBillPayment to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for ScheduledBillPayment to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
