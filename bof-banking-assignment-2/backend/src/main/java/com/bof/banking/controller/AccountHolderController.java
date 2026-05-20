package com.bof.banking.controller;

import com.bof.banking.dto.account.AccountHolderResponse;
import com.bof.banking.dto.account.AddAccountHolderRequest;
import com.bof.banking.service.AccountHolderService;
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
 * REST controller for managing account holders.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/holders")
@RequiredArgsConstructor
public class AccountHolderController {

    private final AccountHolderService accountHolderService;

    /**
     * Add an account holder to an account.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<AccountHolderResponse> addAccountHolder(
            @PathVariable Long accountId,
            @Valid @RequestBody AddAccountHolderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        AccountHolderResponse response = accountHolderService.addAccountHolder(
            accountId, 
            request, 
            userDetails.getUsername()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all account holders for an account.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<List<AccountHolderResponse>> getAccountHolders(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<AccountHolderResponse> holders = accountHolderService.getAccountHolders(
            accountId, 
            userDetails.getUsername()
        );
        return ResponseEntity.ok(holders);
    }

    /**
     * Remove an account holder from an account.
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<Void> removeAccountHolder(
            @PathVariable Long accountId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        accountHolderService.removeAccountHolder(
            accountId, 
            userId, 
            userDetails.getUsername()
        );
        return ResponseEntity.noContent().build();
    }
}
