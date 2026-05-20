package com.bof.banking.dto.tax;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload for the mandatory FRCS end-of-year interest summary report.
 * Boolean fields use {@code @JsonProperty} to preserve the {@code is} prefix
 * in JSON serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrcsInterestSummaryReport {

    private String    bankName;
    private String    bankTin;
    private int       taxYear;
    private LocalDate reportGeneratedDate;
    private List<UserInterestRecord> userRecords;
    private BigDecimal totalGrossInterestPaid;
    private BigDecimal totalNrwhtWithheld;
    private BigDecimal totalRiwtWithheld;
    private BigDecimal totalNrwhtRefunded;
    private BigDecimal totalWithholdingToRemit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInterestRecord {

        private String customerId;
        private String fullName;
        private String tinNumber;

        @JsonProperty("isResident")
        private boolean resident;

        @JsonProperty("isSeniorCitizen")
        private boolean seniorCitizen;

        private BigDecimal grossInterestEarned;
        private BigDecimal nrwhtWithheld;
        private BigDecimal riwtWithheld;
        private BigDecimal netInterestPaid;
        private String     exemptionReason;
        private boolean customerSubmitted;
        private LocalDate customerSubmittedDate;

        /** True when NRWHT was charged but later refunded after TIN registration. */
        @Builder.Default
        private boolean nrwhtRefunded = false;

        private String nrwhtRefundReference;
    }
}