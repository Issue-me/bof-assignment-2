package com.bof.banking.service;

import com.bof.banking.dto.billpayment.ScheduledBillPaymentRequest;
import com.bof.banking.dto.billpayment.ScheduledBillPaymentResponse;
import com.bof.banking.dto.billpayment.BillerInvoiceResponse;
import com.bof.banking.dto.billpayment.AutoPayHistoryItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for scheduled bill payment operations.
 */
public interface ScheduledBillPaymentService {

    /**
     * Creates a new scheduled bill payment for the authenticated user.
     */
    ScheduledBillPaymentResponse createScheduledBillPayment(String userEmail, ScheduledBillPaymentRequest request);

    /**
     * Retrieves a scheduled bill payment by ID (ensures ownership).
     */
    ScheduledBillPaymentResponse getScheduledBillPaymentById(Long id, String userEmail);

    /**
     * Retrieves all scheduled bill payments for a user.
     */
    List<ScheduledBillPaymentResponse> getScheduledBillPaymentsByUser(String userEmail);

    /**
     * Retrieves scheduled bill payments for a user (paginated).
     */
    Page<ScheduledBillPaymentResponse> getScheduledBillPaymentsByUser(String userEmail, Pageable pageable);

    /**
     * Updates a scheduled bill payment (amount, frequency, dates, description, status).
     */
    ScheduledBillPaymentResponse updateScheduledBillPayment(
            Long id,
            String userEmail,
            ScheduledBillPaymentRequest request);

    /**
     * Cancels a scheduled bill payment by marking status as CANCELLED.
     */
    ScheduledBillPaymentResponse cancelScheduledBillPayment(Long id, String userEmail);

    /**
     * Pauses a scheduled bill payment by marking status as PAUSED.
     */
    ScheduledBillPaymentResponse pauseScheduledBillPayment(Long id, String userEmail);

    /**
     * Resumes a paused scheduled bill payment by marking status as ACTIVE.
     */
    ScheduledBillPaymentResponse resumeScheduledBillPayment(Long id, String userEmail);

    ScheduledBillPaymentResponse setAutoPayEnabled(
            Long id,
            String userEmail,
            boolean enabled,
            boolean payPendingBills);

    List<BillerInvoiceResponse> getInvoicesForScheduledPayment(Long id, String userEmail);

    BillerInvoiceResponse payInvoiceNow(
            Long scheduledPaymentId,
            Long invoiceId,
            String userEmail,
            String idempotencyKey);

    List<AutoPayHistoryItemResponse> getAutoPayHistory(Long id, String userEmail);

    /**
     * Executes all due scheduled bill payments (executed by scheduler).
     */
    void executeScheduledPayments();

    void executeScheduledPaymentsManually();
}
