package com.bof.banking.dto.statement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for statement metadata, used when listing available statements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementMetadataResponse {

    private String statementId;

    private String accountNumber;

    private String accountName;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    private LocalDateTime generatedDate;

    private String statementMonth;

    private String statementYear;
}
