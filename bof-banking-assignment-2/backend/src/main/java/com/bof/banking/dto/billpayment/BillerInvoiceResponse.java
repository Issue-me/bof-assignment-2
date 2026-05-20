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
 * Core type for Biller invoice response.
 */
public class BillerInvoiceResponse {
    private Long id;
    private Long billerId;
    private String billerName;
    private String customerReference;
    private Integer invoiceMonth;
    private Integer invoiceYear;
    private BigDecimal invoiceAmount;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private String paymentReference;
    private LocalDateTime paidAt;
}
