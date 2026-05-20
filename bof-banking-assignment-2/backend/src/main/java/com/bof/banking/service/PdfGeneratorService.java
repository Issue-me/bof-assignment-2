package com.bof.banking.service;

import com.bof.banking.dto.statement.StatementResponse;

/**
 * Service interface for PDF generation.
 */
public interface PdfGeneratorService {

    /**
     * Generate a bank statement as PDF.
     *
     * @param statement the statement data
     * @return PDF as byte array
     */
    byte[] generateStatementPdf(StatementResponse statement);
}
