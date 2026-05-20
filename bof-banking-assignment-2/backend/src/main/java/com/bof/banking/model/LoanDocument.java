package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores metadata for documents uploaded as part of a loan application.
 * The actual file bytes are stored in the database as a BLOB (byte[]).
 *
 * Document types accepted:
 *   - RESIDENCY_EVIDENCE    (utility bill, rental agreement, etc.)
 *   - BANK_STATEMENT        (last 3 months)
 *   - PRIMARY_ID            (passport, national ID, driver's licence)
 *   - EMPLOYMENT_DOCUMENT   (employment letter, payslip, business registration)
 *   - OTHER
 */
@Entity
@Table(name = "loan_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    /** Human-readable category e.g. PRIMARY_ID */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** Original filename as uploaded by the customer */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** MIME type e.g. application/pdf, image/jpeg */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** File size in bytes */
    @Column(name = "file_size")
    private Long fileSize;

    /** Raw file content — stored in DB */
    @Lob
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    /** Email of the user who uploaded this document */
    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for LoanDocument to keep entity state consistent.
     */
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
