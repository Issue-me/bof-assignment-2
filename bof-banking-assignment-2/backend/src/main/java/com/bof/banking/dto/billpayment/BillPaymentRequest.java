package com.bof.banking.dto.billpayment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for bill payment requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentRequest {

    @NotNull(message = "Biller ID is required")
    private Long billerId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String description;

    @NotNull(message = "Source account ID is required")
    private Long sourceAccountId;

    private LocalDateTime scheduledDate;
}
