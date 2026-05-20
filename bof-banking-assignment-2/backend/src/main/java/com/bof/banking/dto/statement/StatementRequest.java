package com.bof.banking.dto.statement;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for bank statement request.
 * Supports date range filtering for statement generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotNull(message = "Statement start date is required")
    private LocalDate fromDate;

    @NotNull(message = "Statement end date is required")
    private LocalDate toDate;
}
