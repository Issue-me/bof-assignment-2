package com.bof.banking.controller;

import com.bof.banking.dto.loan.LoanRepaymentRequest;
import com.bof.banking.dto.loan.LoanRepaymentResponse;
import com.bof.banking.dto.loan.LoanRequest;
import com.bof.banking.dto.loan.LoanResponse;
import com.bof.banking.service.LoanRepaymentService;
import com.bof.banking.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Unified REST controller for all loan operations.
 *
 * Root cause fix: the original LoanController only injected LoanService.
 * LoanRepaymentService was never wired in, so the repayment endpoints
 * POST /api/loans/repay and GET /api/loans/{id}/repayments did not exist.
 * The frontend's "View history" button called a non-existent route and
 * received a 404 which was swallowed silently by the catch block, leaving
 * the history panel permanently empty.
 *
 * Customer endpoints:
 *   POST /api/loans/apply              — submit application
 *   GET  /api/loans                    — list my loans
 *   GET  /api/loans/{id}               — single loan
 *   POST /api/loans/repay              — make a repayment
 *   GET  /api/loans/{id}/repayments    — repayment history for a loan
 *
 * Admin endpoints:
 *   GET  /api/loans/admin/all          — all applications
 *   GET  /api/loans/admin/pending      — pending only
 *   POST /api/loans/{id}/approve       — approve
 *   POST /api/loans/{id}/reject        — reject with reason
 */
@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService          loanService;
    private final LoanRepaymentService repaymentService; 

    // ── Customer: applications ─────────────────────────────────────────

    @PostMapping("/apply")
    public ResponseEntity<LoanResponse> apply(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LoanRequest request) {
        return ResponseEntity.ok(loanService.apply(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<LoanResponse>> getMyLoans(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(loanService.getMyLoans(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getLoanById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(loanService.getLoanById(userDetails.getUsername(), id));
    }

    // ── Customer: repayments ───────────────────────────────────────────

    @PostMapping("/repay")
    public ResponseEntity<LoanRepaymentResponse> repay(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.ok(
            repaymentService.makeRepayment(userDetails.getUsername(), request)
        );
    }

    @GetMapping("/{id}/repayments")
    public ResponseEntity<List<LoanRepaymentResponse>> getRepaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(
            repaymentService.getRepaymentHistory(userDetails.getUsername(), id)
        );
    }

    // ── Admin ──────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Returns all loans data.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<List<LoanResponse>> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }

    @GetMapping("/admin/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Returns pending loans data.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<List<LoanResponse>> getPendingLoans() {
        return ResponseEntity.ok(loanService.getPendingLoans());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Handles approve loan.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<LoanResponse> approveLoan(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.approveLoan(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<LoanResponse> rejectLoan(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Application did not meet lending criteria.");
        return ResponseEntity.ok(loanService.rejectLoan(id, reason));
    }
}
