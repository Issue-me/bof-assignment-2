package com.bof.banking.dto.interest;
 
import jakarta.validation.constraints.*;
import lombok.Data;
 
import java.math.BigDecimal;
import java.time.LocalDate;
 
/**
 * Request body for setting a new savings interest rate.
 *
 * <p>{@code annualRate} is accepted as a decimal fraction (e.g. {@code 0.035}
 * for 3.5%), NOT as a percentage integer. The frontend should divide the
 * displayed percentage by 100 before sending.
 */
@Data
public class InterestRateRequest {
 
    /**
     * Annual interest rate as a decimal fraction.
     * Must be between 0.0001 (0.01%) and 1.0000 (100%).
     * Example: 0.035 = 3.5%
     */
    @NotNull(message = "Annual rate is required")
    @DecimalMin(value = "0.0001", message = "Annual rate must be at least 0.01%")
    @DecimalMax(value = "1.0000", message = "Annual rate cannot exceed 100%")
    private BigDecimal annualRate;
 
    /**
     * The date from which this rate is effective.
     * May be today, a past date (backdating), or a future date (pre-scheduling).
     */
    @NotNull(message = "Effective date is required")
    private LocalDate effectiveFrom;
 
    /**
     * Optional reason or reference for the change.
     * e.g. "RBF directive Q1 2026 — ref RBF/2026/001"
     */
    @Size(max = 255, message = "Change reason must not exceed 255 characters")
    private String changeReason;
}