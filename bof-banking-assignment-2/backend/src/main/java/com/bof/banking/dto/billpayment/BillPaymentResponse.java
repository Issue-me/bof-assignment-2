package com.bof.banking.dto.billpayment;

import com.bof.banking.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for bill payment responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentResponse {
    private Long id;
    private String paymentReference;
    private Long billerId;
    private String accountNumber;
    private BigDecimal amount;
    private String description;
    private String sourceAccountNumber;
    private PaymentStatus status;
    private LocalDateTime scheduledDate;
    private LocalDateTime processedDate;
    private LocalDateTime createdAt;
}
