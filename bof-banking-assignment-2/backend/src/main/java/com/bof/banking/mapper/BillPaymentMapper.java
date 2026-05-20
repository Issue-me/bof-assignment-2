package com.bof.banking.mapper;

import com.bof.banking.dto.billpayment.BillPaymentResponse;
import com.bof.banking.model.BillPayment;
import org.springframework.stereotype.Component;

/**
 * Mapper for BillPayment entity to DTO conversions.
 */
@Component
public class BillPaymentMapper {

    /**
     * Handles to response.
     * @param billPayment the bill Payment.
     * @return the result of the operation.
     */
    public BillPaymentResponse toResponse(BillPayment billPayment) {
        if (billPayment == null) {
            return null;
        }
        return BillPaymentResponse.builder()
                .id(billPayment.getId())
                .paymentReference(billPayment.getPaymentReference())
            .billerId(billPayment.getBiller() != null ? billPayment.getBiller().getId() : null)
                .accountNumber(billPayment.getAccountNumber())
                .amount(billPayment.getAmount())
                .description(billPayment.getDescription())
                .sourceAccountNumber(billPayment.getSourceAccount() != null ?
                        billPayment.getSourceAccount().getAccountNumber() : null)
                .status(billPayment.getStatus())
                .scheduledDate(billPayment.getScheduledDate())
                .processedDate(billPayment.getProcessedDate())
                .createdAt(billPayment.getCreatedAt())
                .build();
    }
}
