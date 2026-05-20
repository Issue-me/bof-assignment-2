package com.bof.banking.controller;

import com.bof.banking.dto.statement.StatementMetadataResponse;
import com.bof.banking.dto.statement.StatementRequest;
import com.bof.banking.dto.statement.StatementResponse;
import com.bof.banking.service.StatementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for bank statement endpoints.
 * 
 * All endpoints require CUSTOMER role - only customers can download their own statements.
 */
@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class StatementController {

    private final StatementService statementService;

    /**
     * Get a list of available statements for the customer's account.
     *
     * @param userDetails authenticated customer
     * @param accountId   the account ID
     * @return list of available statement metadata
     */
    @GetMapping
    public ResponseEntity<List<StatementMetadataResponse>> getAvailableStatements(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long accountId) {
        List<StatementMetadataResponse> statements = statementService.getAvailableStatements(
                userDetails.getUsername(), accountId);
        return ResponseEntity.ok(statements);
    }

    /**
     * Generate and return a bank statement for a date range.
     *
     * @param userDetails authenticated customer
     * @param request     statement request with account ID and date range
     * @return statement data with transactions
     */
    @PostMapping
    public ResponseEntity<StatementResponse> generateStatement(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody StatementRequest request) {
        StatementResponse statement = statementService.generateStatement(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(statement);
    }

    /**
     * Download a bank statement as PDF.
     * Returns the PDF file as a byte stream with proper headers for browser download.
     *
     * @param userDetails authenticated customer
     * @param accountId   the account ID
     * @param fromDate    statement start date
     * @param toDate      statement end date
     * @return PDF file as byte array
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadStatementPdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        StatementRequest request = StatementRequest.builder()
                .accountId(accountId)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        byte[] pdfContent = statementService.generateStatementPdf(userDetails.getUsername(), request);

        // Set response headers for PDF download
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("statement_" + fromDate + "_to_" + toDate + ".pdf")
                        .build()
        );
        headers.setContentLength(pdfContent.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}
