package com.bof.banking.dto.billpayment;

import com.bof.banking.model.enums.ScheduleFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for creating or updating scheduled bill payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledBillPaymentRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotNull(message = "Biller ID is required")
    private Long billerId;

    @NotNull(message = "Bill reference (customer account number) is required")
    private String billReference;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Frequency is required")
    private ScheduleFrequency frequency;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean autoPayEnabled;

    @NotNull(message = "Recurring approval is required")
    private Boolean approvalGiven;

    private String description;
}
