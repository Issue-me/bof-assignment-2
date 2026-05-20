package com.bof.banking.dto.billpayment;

import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.ScheduleFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for scheduled bill payment responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledBillPaymentResponse {

    private Long id;
    private Long accountId;
    private String accountNumber;
    private Long billerId;
    private String billerName;
    private String billReference;
    private BigDecimal amount;
    private ScheduleFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecutionDate;
    private Boolean autoPayEnabled;
    private Boolean approvalGiven;
    private Integer lastProcessedMonth;
    private Integer lastProcessedYear;
    private LocalDateTime lastAttemptAt;
    private String lastFailureReason;
    private PaymentStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
