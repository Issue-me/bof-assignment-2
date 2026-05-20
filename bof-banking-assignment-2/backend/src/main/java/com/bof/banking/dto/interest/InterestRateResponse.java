package com.bof.banking.dto.interest;
 
import lombok.*;
 
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
 
/**
 * Single rate record returned to the frontend.
 * {@code effectiveTo} is derived (not stored) — it is the day before
 * the next rate's {@code effectiveFrom}, or null if this is the current
 * or a future-scheduled rate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestRateResponse {
 
    private Long       id;
 
    /** Annual rate as decimal, e.g. 0.0350 */
    private BigDecimal annualRate;
 
    /** Annual rate as percentage, e.g. 3.500000 */
    private BigDecimal annualRatePercent;
 
    /** Daily rate = annualRate / 365, e.g. 0.0000958904 */
    private BigDecimal dailyRate;
 
    /** Date this rate became / becomes effective (inclusive). */
    private LocalDate  effectiveFrom;
 
    /**
     * Derived: the last date this rate was in effect (inclusive).
     * Null for the currently active rate and for future-scheduled rates.
     */
    private LocalDate  effectiveTo;
 
    private String     changeReason;
    private String     setBy;
    private LocalDateTime createdAt;
 
    /**
     * One of: {@code ACTIVE}, {@code SCHEDULED}, {@code SUPERSEDED}.
     * <ul>
     *   <li>ACTIVE – currently in effect today</li>
     *   <li>SCHEDULED – effectiveFrom is in the future</li>
     *   <li>SUPERSEDED – a newer rate has taken over</li>
     * </ul>
     */
    private String     status;
}