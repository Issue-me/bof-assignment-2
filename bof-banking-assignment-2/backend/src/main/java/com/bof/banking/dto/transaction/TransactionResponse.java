package com.bof.banking.dto.transaction;

import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for transaction responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private String referenceNumber;
    private TransactionType transactionType;
    private String entryType;
    private BigDecimal amount;
    private String description;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private PaymentStatus status;
    private BigDecimal balanceAfter;
    private LocalDateTime transactionDate;
}
