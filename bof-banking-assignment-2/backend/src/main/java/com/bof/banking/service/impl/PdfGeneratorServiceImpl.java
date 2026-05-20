package com.bof.banking.service.impl;

import com.bof.banking.dto.statement.StatementLineItemResponse;
import com.bof.banking.dto.statement.StatementResponse;
import com.bof.banking.service.PdfGeneratorService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of {@link PdfGeneratorService} for generating bank statement PDFs using iText.
 */
@Slf4j
@Service
public class PdfGeneratorServiceImpl implements PdfGeneratorService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final float MARGIN = 20f;

    @Override
    /**
     * Handles generate statement pdf.
     * @param statement the statement.
     * @return the result of the operation.
     */
    public byte[] generateStatementPdf(StatementResponse statement) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addHeader(document, statement);
            addAccountInfo(document, statement);
            addStatementPeriod(document, statement);
            addSummary(document, statement);
            addTransactionsTable(document, statement);
            addFooter(document, statement);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException e) {
            log.error("Error generating PDF statement", e);
            throw new RuntimeException("Failed to generate PDF statement", e);
        }
    }

    private void addHeader(Document document, StatementResponse statement) throws DocumentException {
        // Title
        Paragraph title = new Paragraph("BANK STATEMENT", new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        // Bank Info
        Paragraph bankInfo = new Paragraph();
        bankInfo.add(new Chunk(statement.getBankName(), new Font(Font.FontFamily.HELVETICA, 9)));
        bankInfo.add(new Chunk("\n", new Font(Font.FontFamily.HELVETICA, 9)));
        bankInfo.add(new Chunk(statement.getBankAddress(), new Font(Font.FontFamily.HELVETICA, 9)));
        bankInfo.add(new Chunk("\n", new Font(Font.FontFamily.HELVETICA, 9)));
        bankInfo.add(new Chunk("Phone: " + statement.getBankPhone(), new Font(Font.FontFamily.HELVETICA, 9)));
        bankInfo.setAlignment(Element.ALIGN_CENTER);
        bankInfo.setSpacingAfter(20);
        document.add(bankInfo);
    }

    private void addAccountInfo(Document document, StatementResponse statement) throws DocumentException {
        // Create table for account info
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        table.setSpacingAfter(20);

        // Left column - Customer Info
        PdfPCell customerLabel = new PdfPCell(new Paragraph("CUSTOMER INFORMATION", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)));
        customerLabel.setBorder(Rectangle.NO_BORDER);
        table.addCell(customerLabel);

        PdfPCell spacer = new PdfPCell();
        spacer.setBorder(Rectangle.NO_BORDER);
        table.addCell(spacer);

        String customerInfo = statement.getCustomerName() + "\n" +
                statement.getCustomerEmail() + "\n" +
                statement.getCustomerPhone();
        PdfPCell customerData = new PdfPCell(new Paragraph(customerInfo, new Font(Font.FontFamily.HELVETICA, 9)));
        customerData.setBorder(Rectangle.NO_BORDER);
        table.addCell(customerData);

        // Right column - Account Info
        String accountInfo = "Account Number: " + statement.getAccountNumber() + "\n" +
                "Account Type: " + statement.getAccountType() + "\n" +
                "Account Name: " + (statement.getAccountName() != null ? statement.getAccountName() : "N/A");
        PdfPCell accountData = new PdfPCell(new Paragraph(accountInfo, new Font(Font.FontFamily.HELVETICA, 9)));
        accountData.setBorder(Rectangle.NO_BORDER);
        table.addCell(accountData);

        document.add(table);
    }

    private void addStatementPeriod(Document document, StatementResponse statement) throws DocumentException {
        String periodText = "Statement Period: " + 
                statement.getStatementFromDate().format(DATE_FORMATTER) + " to " +
                statement.getStatementToDate().format(DATE_FORMATTER) + " | " +
                "Generated: " + statement.getGeneratedDate().format(DATE_FORMATTER);

        Paragraph period = new Paragraph(periodText, new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD));
        period.setAlignment(Element.ALIGN_LEFT);
        period.setSpacingAfter(15);
        period.setSpacingBefore(10);
        document.add(period);
    }

    private void addSummary(Document document, StatementResponse statement) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10);

        // Opening Balance
        PdfPCell openingLabel = new PdfPCell(new Paragraph("Opening Balance", labelFont));
        openingLabel.setBackgroundColor(new BaseColor(200, 200, 200));
        table.addCell(openingLabel);

        PdfPCell openingValue = new PdfPCell(new Paragraph(formatCurrency(statement.getOpeningBalance()), valueFont));
        openingValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        openingValue.setBackgroundColor(new BaseColor(200, 200, 200));
        table.addCell(openingValue);

        // Closing Balance
        PdfPCell closingLabel = new PdfPCell(new Paragraph("Closing Balance", labelFont));
        closingLabel.setBackgroundColor(new BaseColor(200, 200, 200));
        table.addCell(closingLabel);

        PdfPCell closingValue = new PdfPCell(new Paragraph(formatCurrency(statement.getClosingBalance()), valueFont));
        closingValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        closingValue.setBackgroundColor(new BaseColor(200, 200, 200));
        table.addCell(closingValue);

        // Total Debits
        PdfPCell debitsLabel = new PdfPCell(new Paragraph("Total Debits", labelFont));
        table.addCell(debitsLabel);

        PdfPCell debitsValue = new PdfPCell(new Paragraph(formatCurrency(statement.getTotalDebits()), valueFont));
        debitsValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(debitsValue);

        // Total Credits
        PdfPCell creditsLabel = new PdfPCell(new Paragraph("Total Credits", labelFont));
        table.addCell(creditsLabel);

        PdfPCell creditsValue = new PdfPCell(new Paragraph(formatCurrency(statement.getTotalCredits()), valueFont));
        creditsValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(creditsValue);

        document.add(table);
    }

    private void addTransactionsTable(Document document, StatementResponse statement) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 2f, 1f, 1f, 1f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        Font headerFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        String[] headers = {"Date", "Description", "Debit", "Credit", "Balance", "Reference"};
        
        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Paragraph(header, headerFont));
            headerCell.setBackgroundColor(new BaseColor(50, 50, 50));
            headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerCell.setPadding(5);
            table.addCell(headerCell);
        }

        Font dataFont = new Font(Font.FontFamily.HELVETICA, 8);
        
        // Transaction rows
        if (statement.getTransactions() != null && !statement.getTransactions().isEmpty()) {
            for (StatementLineItemResponse transaction : statement.getTransactions()) {
                table.addCell(new PdfPCell(new Paragraph(transaction.getTransactionDate(), dataFont)));
                table.addCell(new PdfPCell(new Paragraph(transaction.getDescription(), dataFont)));

                String debitStr = transaction.getDebit() != null ? 
                        formatCurrency(transaction.getDebit()) : "-";
                PdfPCell debitCell = new PdfPCell(new Paragraph(debitStr, dataFont));
                debitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(debitCell);

                String creditStr = transaction.getCredit() != null ? 
                        formatCurrency(transaction.getCredit()) : "-";
                PdfPCell creditCell = new PdfPCell(new Paragraph(creditStr, dataFont));
                creditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(creditCell);

                PdfPCell balanceCell = new PdfPCell(new Paragraph(formatCurrency(transaction.getRunningBalance()), dataFont));
                balanceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(balanceCell);

                table.addCell(new PdfPCell(new Paragraph(transaction.getReferenceNumber(), dataFont)));
            }
        } else {
            PdfPCell noTransCell = new PdfPCell(new Paragraph("No transactions in this period", dataFont));
            noTransCell.setColspan(6);
            noTransCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(noTransCell);
        }

        document.add(table);
    }

    private void addFooter(Document document, StatementResponse statement) throws DocumentException {
        Paragraph footer = new Paragraph();
        footer.setSpacingBefore(30);
        footer.add(new Chunk("This is a computer-generated statement and does not require a signature.\n", 
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, new BaseColor(100, 100, 100))));
        footer.add(new Chunk("Statement ID: " + statement.getStatementId(), 
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, new BaseColor(100, 100, 100))));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return "FJD " + String.format("%.2f", amount);
    }
}
