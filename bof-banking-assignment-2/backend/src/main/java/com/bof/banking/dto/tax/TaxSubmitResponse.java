package com.bof.banking.dto.tax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Returned to the user after successfully submitting their annual tax return.
 *
 * <p>The {@code referenceNumber} follows the format {@code FRCS-YYYY-XXXXXXXX}
 * (e.g. {@code FRCS-2024-A3F9C12B}) and should be kept by the user
 * as proof of submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxSubmitResponse {

    /**
     * Unique FRCS submission reference.
     * Format: {@code FRCS-<year>-<8-char UUID fragment>}.
     */
    private String referenceNumber;

    /** Human-readable confirmation message. */
    private String message;

    /** Server timestamp when the submission was recorded. */
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}