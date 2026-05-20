package com.bof.banking.mapper;

import com.bof.banking.dto.billpayment.ScheduledBillPaymentResponse;
import com.bof.banking.model.ScheduledBillPayment;
import org.springframework.stereotype.Component;

/**
 * Mapper for ScheduledBillPayment entity to DTO conversions.
 */
@Component
public class ScheduledBillPaymentMapper {

    /**
     * Handles to response.
     * @param payment the payment.
     * @return the result of the operation.
     */
    public ScheduledBillPaymentResponse toResponse(ScheduledBillPayment payment) {
        if (payment == null) {
            return null;
        }
        return ScheduledBillPaymentResponse.builder()
                .id(payment.getId())
                .accountId(payment.getAccount() != null ? payment.getAccount().getId() : null)
                .accountNumber(payment.getAccount() != null ? payment.getAccount().getAccountNumber() : null)
                .billerId(payment.getBiller() != null ? payment.getBiller().getId() : null)
                .billerName(payment.getBiller() != null ? payment.getBiller().getBillerName() : null)
                .billReference(payment.getBillReference())
                .amount(payment.getAmount())
                .frequency(payment.getFrequency())
                .startDate(payment.getStartDate())
                .endDate(payment.getEndDate())
                .nextExecutionDate(payment.getNextExecutionDate())
                .autoPayEnabled(payment.isAutoPayEnabled())
                .approvalGiven(payment.isApprovalGiven())
                .lastProcessedMonth(payment.getLastProcessedMonth())
                .lastProcessedYear(payment.getLastProcessedYear())
                .lastAttemptAt(payment.getLastAttemptAt())
                .lastFailureReason(payment.getLastFailureReason())
                .status(payment.getStatus())
                .description(payment.getDescription())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
