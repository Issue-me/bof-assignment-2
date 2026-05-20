package com.bof.banking.dto.statement;

import com.bof.banking.model.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing a single transaction line item in a bank statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementLineItemResponse {

    private String transactionDate;

    private String description;

    private TransactionType type;

    private BigDecimal debit;

    private BigDecimal credit;

    private BigDecimal runningBalance;

    private String referenceNumber;

    private LocalDateTime timestamp;
}
