package com.bof.banking.dto.interest;
 
import lombok.*;
 
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
 
/**
 * Top-level response for GET /api/interest-rates.
 * Includes the currently active rate figures plus the full history list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestRateHistoryResponse {
 
    /** Current annual rate as decimal. Zero if no rate configured yet. */
    private BigDecimal currentAnnualRate;
 
    /** Current annual rate as percentage for display. */
    private BigDecimal currentAnnualRatePercent;
 
    /** Current daily rate = currentAnnualRate / 365. */
    private BigDecimal currentDailyRate;
 
    /** Current daily rate as percentage for display. */
    private BigDecimal currentDailyRatePercent;
 
    /** Date the current rate became effective. Null if no rate set. */
    private LocalDate  effectiveSince;
 
    /** False if no rate has been configured yet — triggers warning on frontend. */
    private boolean    hasRate;
 
    /** Full history, newest first. */
    private List<InterestRateResponse> history;
}