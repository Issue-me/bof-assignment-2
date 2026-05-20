package com.bof.banking.model;

import com.bof.banking.model.enums.RiwtExemptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RIWT Exemption submission.
 *
 * FIX: fileContent stores the certificate file as a BLOB in the database.
 * filePath is kept for backwards compatibility but is no longer populated.
 * This ensures the file is always retrievable regardless of server filesystem.
 */
@Entity
@Table(
    name = "riwt_exemptions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_riwt_user_year",
            columnNames = {"user_id", "tax_year"}
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * RIWT Exemption submission. FIX: fileContent stores the certificate file as a BLOB in the database. filePath is kept for backwards compatibility but is no longer populated. This ensures the file is always retrievable regardless of server filesystem.
 */
public class RiwtExemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiwtExemptionStatus status;

    /** Original filename of the uploaded certificate */
    @Column(name = "file_name", length = 500)
    private String fileName;

    /**
     * Certificate file stored as a BLOB in the database.
     * FIX: replaces the file-path approach which failed on Windows.
     */
    @Lob
    @Column(name = "file_content")
    private byte[] fileContent;

    /**
     * Kept for backwards compatibility — no longer populated.
     * File is now stored in fileContent above.
     */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /** Amount of RIWT withheld at time of submission (for admin context) */
    @Column(name = "riwt_withheld", precision = 19, scale = 2)
    private BigDecimal riwtWithheld;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Teller/admin who reviewed this submission */
    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;
}
