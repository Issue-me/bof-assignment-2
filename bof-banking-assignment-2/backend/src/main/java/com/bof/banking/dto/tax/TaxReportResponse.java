package com.bof.banking.dto.tax;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Core type for T ax Re po rt Re sp on se.
 */
public class TaxReportResponse {

    // ── Identity ─────────────────────────────────────────────────────────────
    private String fullName;
    private String customerId;
    private String tinNumber;
    private int    taxYear;
    private String status;

    // FIX: field renamed to "resident" (no "is" prefix).
    // Lombok generates: getResident(), builder method .resident(value).
    // @JsonProperty forces the JSON key to be "isResident" so the frontend's
    // report.isResident still works without any changes to the frontend code.
    @JsonProperty("isResident")
    private boolean resident;

    @JsonProperty("isSeniorCitizen")
    private boolean seniorCitizen;

    // ── Interest rate ─────────────────────────────────────────────────────────
    private String interestRate;   // e.g. "3.5% p.a."

    // ── RIWT exemption ────────────────────────────────────────────────────────
    @JsonProperty("riwtExempt")
    private boolean riwtExempt;

    @JsonProperty("riwtRejected")
    private boolean riwtRejected;

    // ── Interest ──────────────────────────────────────────────────────────────
    private BigDecimal interestEarned;
    private BigDecimal riwtWithheld;
    private BigDecimal nrwhtWithheld;

    /** True when NRWHT was charged earlier this year but has since been refunded because the customer registered their TIN. */
    @Builder.Default
    private boolean nrwhtRefunded = false;

    /** The transaction reference of the refund credit, e.g. NRWHT-REFUND-2026-... */
    private String nrwhtRefundReference;
    
    private BigDecimal netInterestPaid;

    // ── Income ────────────────────────────────────────────────────────────────
    private BigDecimal grossIncome;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal taxableIncome;
    private long       transactionCount;

    // ── Tax ───────────────────────────────────────────────────────────────────
    private BigDecimal payeOwed;
    private BigDecimal vatOnFees;
    private BigDecimal bankFeesCharged;
    private BigDecimal fnpfEmployee;
    private BigDecimal fnpfEmployer;
    private BigDecimal totalTaxOwed;
    private String frcsReference;
    private boolean submittedToFrcs;
    private LocalDate frcsSubmissionDate;
    
    // ── Monthly breakdown ─────────────────────────────────────────────────────
    private List<MonthlyBreakdown> monthlyBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyBreakdown {
        private String     month;
        private BigDecimal income;
        private BigDecimal interest;
        private BigDecimal taxWithheld;
    }
}
