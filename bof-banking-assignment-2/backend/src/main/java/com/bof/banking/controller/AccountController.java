package com.bof.banking.controller;

import com.bof.banking.dto.account.AccountRequest;
import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.dto.account.AccountResponseDto;
import com.bof.banking.dto.account.AccountSummaryResponse;
import com.bof.banking.dto.account.CreateAccountRequestDto;
import com.bof.banking.model.Role;
import com.bof.banking.security.CustomUserDetails;
import com.bof.banking.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for account management endpoints.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<AccountResponseDto> createAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAccountRequestDto request) {
        AccountResponseDto response = accountService.createAccount(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','ADMIN')")
    public ResponseEntity<AccountResponseDto> getAccountById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        if (isTellerOrAdmin(userDetails)) {
            AccountResponse response = accountService.getAccountById(id);
            return ResponseEntity.ok(toResponseDto(response));
        }
        return ResponseEntity.ok(accountService.getAccountById(userDetails.getUsername(), id));
    }

    @GetMapping("/number/{accountNumber}")
    /**
     * Returns account by number data.
     * @param accountNumber the account Number.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber));
    }

    /**
     * Get system-wide account summary statistics (TELLER/ADMIN only).
     * Provides overview metrics for admin dashboard.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<AccountSummaryResponse> getAccountsSummary() {
        AccountSummaryResponse summary = accountService.getAccountsSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','ADMIN')")
    public ResponseEntity<List<AccountResponseDto>> getMyAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (isTellerOrAdmin(userDetails)) {
            return ResponseEntity.ok(accountService.getAllAccounts());
        }
        return ResponseEntity.ok(accountService.getAccountsByUser(userDetails.getUsername()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<AccountResponse>> getMyActiveAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accountService.getActiveAccountsByUser(userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Handles close account.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Void> closeAccount(@PathVariable Long id) {
        accountService.closeAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Handles activate account.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Void> activateAccount(@PathVariable Long id) {
        accountService.activateAccount(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isTellerOrAdmin(UserDetails userDetails) {
        if (!(userDetails instanceof CustomUserDetails customUserDetails)) {
            return false;
        }

        Role role = customUserDetails.getUser().getRole();
        return role == Role.TELLER || role == Role.ADMIN;
    }

    private AccountResponseDto toResponseDto(AccountResponse response) {
        return AccountResponseDto.builder()
                .id(response.getId())
                .accountNumber(response.getAccountNumber())
                .accountName(response.getAccountName())
                .accountType(response.getAccountType())
                .balance(response.getBalance())
                .interestRate(response.getInterestRate())
                .interestEarned(response.getInterestEarned())
                .active(response.isActive())
                .createdAt(response.getCreatedAt())
                .build();
    }
}
