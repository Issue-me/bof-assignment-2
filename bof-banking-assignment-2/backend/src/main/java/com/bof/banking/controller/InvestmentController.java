package com.bof.banking.controller;

import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for investment endpoints.
 */
@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @PostMapping
    public ResponseEntity<AccountResponse> createInvestment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String investmentType,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) Integer termMonths,
            @RequestParam(required = false) Long linkedAccountId) {
        AccountResponse response = investmentService.createInvestment(
                userDetails.getUsername(), investmentType, amount, termMonths, linkedAccountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    /**
     * Returns investment by id data.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<AccountResponse> getInvestmentById(@PathVariable Long id) {
        return ResponseEntity.ok(investmentService.getInvestmentById(id));
    }

    @GetMapping("/number/{investmentNumber}")
    /**
     * Returns investment by number data.
     * @param investmentNumber the investment Number.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<AccountResponse> getInvestmentByNumber(@PathVariable String investmentNumber) {
        return ResponseEntity.ok(investmentService.getInvestmentByNumber(investmentNumber));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyInvestments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(investmentService.getInvestmentsByUser(userDetails.getUsername()));
    }

    @GetMapping("/page")
    public ResponseEntity<Page<AccountResponse>> getMyInvestmentsPaged(
            @AuthenticationPrincipal UserDetails userDetails, Pageable pageable) {
        return ResponseEntity.ok(investmentService.getInvestmentsByUser(userDetails.getUsername(), pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<List<AccountResponse>> getMyActiveInvestments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(investmentService.getActiveInvestments(userDetails.getUsername()));
    }

    @PostMapping("/{id}/close")
    /**
     * Handles close investment.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<AccountResponse> closeInvestment(@PathVariable Long id) {
        return ResponseEntity.ok(investmentService.closeInvestment(id));
    }
}
