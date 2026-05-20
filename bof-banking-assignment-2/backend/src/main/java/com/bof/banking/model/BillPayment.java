package com.bof.banking.model;

import com.bof.banking.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a bill payment record.
 */
@Entity
@Table(name = "bill_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", unique = true, nullable = false)
    private String paymentReference;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_id")
    private Biller biller;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for BillPayment to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
