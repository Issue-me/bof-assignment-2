package com.bof.banking.service;

import com.bof.banking.dto.billpayment.BillPaymentRequest;
import com.bof.banking.dto.billpayment.BillPaymentResponse;
import com.bof.banking.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for bill payment operations.
 */
public interface BillPaymentService {

    /**
     * Creates a bill payment for the specified user.
     *
     * @param userEmail authenticated user email.
     * @param request validated bill payment request payload.
     * @return created bill payment.
     */
    BillPaymentResponse createBillPayment(String userEmail, BillPaymentRequest request);

    /**
     * Returns a bill payment by internal id.
     *
     * @param id bill payment id.
     * @return matching bill payment.
     */
    BillPaymentResponse getBillPaymentById(Long id);

    /**
     * Returns a bill payment by external payment reference.
     *
     * @param paymentReference external payment reference.
     * @return matching bill payment.
     */
    BillPaymentResponse getBillPaymentByReference(String paymentReference);

    /**
     * Lists bill payments for one user.
     *
     * @param userEmail authenticated user email.
     * @return user bill payments.
     */
    List<BillPaymentResponse> getBillPaymentsByUser(String userEmail);

    /**
     * Lists bill payments for one user with paging.
     *
     * @param userEmail authenticated user email.
     * @param pageable page and sort options.
     * @return paged bill payments.
     */
    Page<BillPaymentResponse> getBillPaymentsByUser(String userEmail, Pageable pageable);

    /**
     * Returns a teller monitoring view of bill payments using optional filters.
     *
     * @param search optional free-text filter.
     * @param billerId optional biller id filter.
     * @param status optional payment status filter.
     * @param fromDate optional lower date/time bound.
     * @param toDate optional upper date/time bound.
     * @param minAmount optional minimum amount.
     * @param maxAmount optional maximum amount.
     * @param pageable page and sort options.
     * @return paged monitoring results.
     */
    Page<BillPaymentResponse> getBillPaymentsForMonitoring(
            String search,
            Long billerId,
            PaymentStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    /**
     * Cancels a bill payment if cancellation rules allow it.
     *
     * @param id bill payment id.
     * @return updated bill payment state.
     */
    BillPaymentResponse cancelBillPayment(Long id);

    /**
     * Executes scheduled bill payments that are due.
     */
    void processScheduledPayments();
}
