package com.bof.banking.service;

import com.bof.banking.dto.transaction.TransactionRequest;
import com.bof.banking.dto.transaction.TransactionResponse;
import com.bof.banking.dto.transaction.TransferInitiationResponse;
import com.bof.banking.dto.transaction.TransferLimitSummaryResponse;
import com.bof.banking.dto.transaction.TransferOtpVerificationRequest;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for transaction operations.
 */
public interface TransactionService {

    TransactionResponse createTransaction(String userEmail, TransactionRequest request);

    TransactionResponse deposit(String userEmail, Long accountId, TransactionRequest request);

    TransactionResponse withdraw(String userEmail, Long accountId, TransactionRequest request);

    TransactionResponse transfer(String userEmail, TransactionRequest request);

    TransferInitiationResponse initiateTransfer(String userEmail, TransactionRequest request);

    TransactionResponse verifyTransferOtp(String userEmail, TransferOtpVerificationRequest request);

    TransferLimitSummaryResponse getTransferLimitSummary(String userEmail);

    TransactionResponse getTransactionById(Long id);

    TransactionResponse getTransactionByReference(String referenceNumber);

        Page<TransactionResponse> getRecentTransactions(
            String userEmail, Long accountId, String entryType, Pageable pageable);

    List<TransactionResponse> getTransactionsByAccount(Long accountId);

    Page<TransactionResponse> getTransactionsByAccount(Long accountId, Pageable pageable);

    List<TransactionResponse> getTransactionsByAccountAndDateRange(
            Long accountId, LocalDateTime startDate, LocalDateTime endDate);

        Page<TransactionResponse> getTransactionsForMonitoring(
            String search,
            TransactionType transactionType,
            PaymentStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}
