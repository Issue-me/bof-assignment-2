package com.bof.banking.model;

import com.bof.banking.model.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a monthly biller invoice used by auto-bill payment.
 */
@Entity
@Table(
        name = "biller_invoices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_biller_invoice_period",
                        columnNames = {"biller_id", "customer_reference", "invoice_month", "invoice_year"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Holds persisted state and relationships for b il le ri nv oi ce within the domain model.
 */
public class BillerInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @Column(name = "customer_reference", nullable = false, length = 80)
    private String customerReference;

    @Column(name = "invoice_month", nullable = false)
    private Integer invoiceMonth;

    @Column(name = "invoice_year", nullable = false)
    private Integer invoiceYear;

    @Column(name = "invoice_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_payment_id")
    private BillPayment billPayment;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for BillerInvoice to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for BillerInvoice to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
