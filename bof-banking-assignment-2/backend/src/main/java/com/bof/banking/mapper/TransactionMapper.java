package com.bof.banking.mapper;

import com.bof.banking.dto.transaction.TransactionResponse;
import com.bof.banking.model.Transaction;
import org.springframework.stereotype.Component;

/**
 * Mapper for Transaction entity to DTO conversions.
 */
@Component
public class TransactionMapper {

    /**
     * Handles to response.
     * @param transaction the transaction.
     * @return the result of the operation.
     */
    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        return TransactionResponse.builder()
                .id(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .sourceAccountNumber(transaction.getSourceAccount() != null ?
                        transaction.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(transaction.getDestinationAccount() != null ?
                        transaction.getDestinationAccount().getAccountNumber() : null)
                .status(transaction.getStatus())
                .balanceAfter(transaction.getBalanceAfter())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }
}
