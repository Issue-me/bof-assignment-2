package com.bof.banking.dto.statement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for complete bank statement with transactions and summary.
 * Contains all information needed to generate a PDF statement or display in UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementResponse {

    // Bank Information
    private String bankName;

    private String bankAddress;

    private String bankPhone;

    // Customer Information
    private String customerName;

    private String customerEmail;

    private String customerPhone;

    // Account Information
    private String accountNumber;

    private String accountType;

    private String accountName;

    // Statement Period
    private LocalDate statementFromDate;

    private LocalDate statementToDate;

    private LocalDate generatedDate;

    // Account Summary
    private BigDecimal openingBalance;

    private BigDecimal closingBalance;

    private BigDecimal totalDebits;

    private BigDecimal totalCredits;

    // Transactions
    private List<StatementLineItemResponse> transactions;

    // Statement ID for download tracking
    private String statementId;
}
