package com.bof.banking.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bof.banking.dto.tax.FrcsInterestSummaryReport;
import com.bof.banking.dto.tax.TaxReportResponse;
import com.bof.banking.dto.tax.TaxSubmitResponse;

/**
 * Service interface for tax report operations.
 *
 * <p>Implementations must resolve the authenticated user via {@code userEmail}
 * (the Spring Security principal username, which is the user's email address).
 *
 * @see com.bof.banking.service.impl.TaxServiceImpl
 */
public interface TaxService {

    /**
     * Build the annual tax report for the authenticated user.
     *
     * <p>Always computes from live transaction data (transient — not persisted).
     * The returned report has {@code status = "DRAFT"} until
     * {@link #submitReturn(String, int)} is called.
     *
     * @param userEmail JWT principal username (email)
     * @param year      tax year (e.g. 2024)
     * @return populated {@link TaxReportResponse}
     */
    TaxReportResponse getReport(String userEmail, int year);

    /**
     * Submit the user's tax return for the given year to FRCS.
     *
     * <p>Persists (or refreshes) the {@link com.bof.banking.model.UserInterestSummary}
     * record and returns a unique FRCS reference number for the user's records.
     *
     * @param userEmail JWT principal username (email)
     * @param year      tax year being submitted
     * @return {@link TaxSubmitResponse} containing the reference number
     */
    TaxSubmitResponse submitReturn(String userEmail, int year);

    /**
     * Generate the mandatory end-of-year FRCS interest summary report
     * covering ALL users who earned interest in the given year.
     *
     * <p>Prefers persisted {@link com.bof.banking.model.UserInterestSummary} records
     * written by the {@link com.bof.banking.scheduler.InterestSummaryScheduler};
     * falls back to live calculation when no persisted record exists yet.
     *
     * <p>Intended to be called by an admin endpoint or scheduled job — not
     * exposed directly to regular users.
     *
     * @param year tax year
     * @return {@link FrcsInterestSummaryReport} ready for FRCS submission
     */
    FrcsInterestSummaryReport generateFrcsInterestSummary(int year);

    Optional<BigDecimal> getPendingNrwhtRefundAmount(Long userId);
}