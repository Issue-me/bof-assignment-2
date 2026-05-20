package com.bof.banking.service;

import com.bof.banking.dto.statement.StatementRequest;
import com.bof.banking.dto.statement.StatementResponse;
import com.bof.banking.dto.statement.StatementMetadataResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for bank statement operations.
 * Handles statement generation, retrieval, and PDF export.
 */
public interface StatementService {

    /**
     * Generate a bank statement for a specific account and date range.
     * Only customers can access their own statements.
     *
     * @param userEmail  the email of the authenticated customer
     * @param request    the statement request containing account ID and date range
     * @return           the complete statement with transactions
     * @throws com.bof.banking.exception.ResourceNotFoundException if account or user not found
     * @throws com.bof.banking.exception.UnauthorizedException if user does not own the account
     * @throws IllegalArgumentException if date range is invalid or exceeds 5 years
     */
    StatementResponse generateStatement(String userEmail, StatementRequest request);

    /**
     * Get available statement metadata for an account.
     * Returns metadata for statements within the last 5 years.
     *
     * @param userEmail  the email of the authenticated customer
     * @param accountId  the account ID
     * @return           list of available statement metadata
     * @throws com.bof.banking.exception.ResourceNotFoundException if account not found
     * @throws com.bof.banking.exception.UnauthorizedException if user does not own the account
     */
    List<StatementMetadataResponse> getAvailableStatements(String userEmail, Long accountId);

    /**
     * Generate a statement PDF as bytes.
     * The PDF includes formatted statement details and transaction history.
     *
     * @param userEmail  the email of the authenticated customer
     * @param request    the statement request
     * @return           PDF file as byte array
     * @throws com.bof.banking.exception.ResourceNotFoundException if account or user not found
     * @throws com.bof.banking.exception.UnauthorizedException if user does not own the account
     */
    byte[] generateStatementPdf(String userEmail, StatementRequest request);
}
