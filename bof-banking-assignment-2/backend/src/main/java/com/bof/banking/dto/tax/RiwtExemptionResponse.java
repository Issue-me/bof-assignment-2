package com.bof.banking.dto.tax;

import lombok.*;

import java.math.BigDecimal;

/**
 * DTO returned by all RIWT exemption endpoints.
 * Field names match exactly what AdminRiwtExemptionsPage.jsx reads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiwtExemptionResponse {

    private Long   id;

    private String customerEmail;
    private String customerName;
    private String customerId;
    private String tinNumber;

    private int    taxYear;
    private String status;           // "PENDING" | "APPROVED" | "REJECTED"

    private String fileName;
    private String fileUrl;          // /api/tax/riwt-exemption/{id}/file

    private BigDecimal riwtWithheld;

    private String rejectionReason;
    private String reviewedBy;

    private String submittedDate;    // formatted for display
    private String reviewedDate;     // nullable
}