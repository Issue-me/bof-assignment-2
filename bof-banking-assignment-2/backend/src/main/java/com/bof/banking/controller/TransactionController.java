package com.bof.banking.controller;

import com.bof.banking.dto.transaction.TransactionRequest;
import com.bof.banking.dto.transaction.TransactionResponse;
import com.bof.banking.dto.transaction.TransferInitiationResponse;
import com.bof.banking.dto.transaction.TransferLimitSummaryResponse;
import com.bof.banking.dto.transaction.TransferOtpVerificationRequest;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for transaction endpoints.
 * <p>
 * RBAC: Only CUSTOMERS can perform financial transactions (deposit, withdraw, transfer).
 * ADMIN and TELLER are prohibited from performing transactions per Role Access Matrix.
 * </p>
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/deposit/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.deposit(userDetails.getUsername(), accountId, request));
    }

    @PostMapping("/withdraw/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.withdraw(userDetails.getUsername(), accountId, request));
    }

    @PostMapping("/transfer/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransferInitiationResponse> initiateTransfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.initiateTransfer(userDetails.getUsername(), request));
    }

    @PostMapping("/transfer/verify-otp")
    public ResponseEntity<TransactionResponse> verifyTransferOtp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransferOtpVerificationRequest request) {
        return ResponseEntity.ok(transactionService.verifyTransferOtp(userDetails.getUsername(), request));
    }

    @GetMapping("/transfer-limits")
    public ResponseEntity<TransferLimitSummaryResponse> getTransferLimits(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.getTransferLimitSummary(userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String entryType,
            Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.getRecentTransactions(
                        userDetails.getUsername(), accountId, entryType, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @GetMapping("/reference/{referenceNumber}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> getTransactionByReference(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String referenceNumber) {
        return ResponseEntity.ok(transactionService.getTransactionByReference(referenceNumber));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountId));
    }

    @GetMapping("/account/{accountId}/page")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<TransactionResponse>> getTransactionsByAccountPaged(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountId, pageable));
    }

    @GetMapping("/account/{accountId}/range")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByDateRange(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccountAndDateRange(accountId, startDate, endDate));
    }

    @GetMapping("/monitoring")
    @PreAuthorize("hasRole('TELLER')")
    public ResponseEntity<Page<TransactionResponse>> monitorTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.getTransactionsForMonitoring(
                        search,
                        transactionType,
                        status,
                        from,
                        to,
                        pageable));
    }
}
