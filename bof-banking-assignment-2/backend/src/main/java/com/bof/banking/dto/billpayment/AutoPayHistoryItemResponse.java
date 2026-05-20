package com.bof.banking.dto.billpayment;

import com.bof.banking.model.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Core type for A ut oP ay Hi st or yI te mR es po ns e.
 */
public class AutoPayHistoryItemResponse {
    private Integer invoiceMonth;
    private Integer invoiceYear;
    private BigDecimal amount;
    private LocalDate dueDate;
    private InvoiceStatus invoiceStatus;
    private String paymentReference;
    private LocalDateTime processedAt;
    private String transactionReference;
}
