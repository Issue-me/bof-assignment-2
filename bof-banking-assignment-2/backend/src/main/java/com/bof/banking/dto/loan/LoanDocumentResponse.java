package com.bof.banking.dto.loan;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Lightweight DTO for document metadata — does NOT include the file bytes.
 * Used for listing documents attached to a loan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDocumentResponse {

    private Long          id;
    private Long          loanId;
    private String        documentType;
    private String        fileName;
    private String        contentType;
    private Long          fileSize;
    private String        uploadedBy;
    private LocalDateTime uploadedAt;
}