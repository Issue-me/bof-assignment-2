package com.bof.banking.controller;

import com.bof.banking.dto.billpayment.AutoPayHistoryItemResponse;
import com.bof.banking.dto.billpayment.AutoPayToggleRequest;
import com.bof.banking.dto.billpayment.BillerInvoiceResponse;
import com.bof.banking.dto.billpayment.ScheduledBillPaymentRequest;
import com.bof.banking.dto.billpayment.ScheduledBillPaymentResponse;
import com.bof.banking.service.ScheduledBillPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bill-payments/scheduled")
@RequiredArgsConstructor
/**
 * REST controller for scheduled bill payment endpoints.
 *
 * Main audience is customer-facing billing flows where users create recurring
 * instructions and optionally enable auto-pay against incoming invoices.
 *
 * RBAC:
 * - CUSTOMER: full lifecycle operations for their own scheduled payments.
 * - ADMIN/TELLER: manual trigger endpoint for operational support.
 */
public class ScheduledBillPaymentController {

    private final ScheduledBillPaymentService scheduledBillPaymentService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Creates a new scheduled bill payment for the logged-in customer.
         *
         * @param userDetails authenticated user context.
         * @param request validated schedule details from the client.
         * @return the newly created scheduled payment.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> createScheduledBillPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ScheduledBillPaymentRequest request) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .createScheduledBillPayment(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Returns one scheduled payment owned by the authenticated customer.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return matching scheduled payment details.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> getScheduledBillPaymentById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .getScheduledBillPaymentById(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Lists all scheduled payments for the authenticated customer.
         *
         * @param userDetails authenticated user context.
         * @return customer-owned scheduled payments.
         */
    public ResponseEntity<List<ScheduledBillPaymentResponse>> getScheduledBillPayments(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ScheduledBillPaymentResponse> response = scheduledBillPaymentService
                .getScheduledBillPaymentsByUser(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Lists scheduled payments with pagination and sorting.
         *
         * @param userDetails authenticated user context.
         * @param pageable page and sort options from request parameters.
         * @return a page of scheduled payments.
         */
    public ResponseEntity<Page<ScheduledBillPaymentResponse>> getScheduledBillPaymentsPaged(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        Page<ScheduledBillPaymentResponse> response = scheduledBillPaymentService
                .getScheduledBillPaymentsByUser(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Updates an existing scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @param request updated schedule payload.
         * @return updated scheduled payment.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> updateScheduledBillPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ScheduledBillPaymentRequest request) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .updateScheduledBillPayment(id, userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Cancels a scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return scheduled payment after cancellation.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> cancelScheduledBillPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .cancelScheduledBillPayment(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/pause")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Temporarily pauses execution of a scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return scheduled payment after pause.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> pauseScheduledBillPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .pauseScheduledBillPayment(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Resumes a previously paused scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return scheduled payment after resume.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> resumeScheduledBillPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .resumeScheduledBillPayment(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/auto-pay")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Enables or disables auto-pay behavior for a scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @param request toggle payload, including optional pay-pending-bills behavior.
         * @return scheduled payment after the auto-pay flag is updated.
         */
    public ResponseEntity<ScheduledBillPaymentResponse> setAutoPayEnabled(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AutoPayToggleRequest request) {
        ScheduledBillPaymentResponse response = scheduledBillPaymentService
                .setAutoPayEnabled(
                        id,
                        userDetails.getUsername(),
                        request.getEnabled(),
                        Boolean.TRUE.equals(request.getPayPendingBills()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Returns invoices relevant to a scheduled payment setup.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return invoice list tied to the scheduled payment context.
         */
    public ResponseEntity<List<BillerInvoiceResponse>> getInvoicesForSetup(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                scheduledBillPaymentService.getInvoicesForScheduledPayment(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/invoices/{invoiceId}/pay")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Pays a single invoice immediately from within the scheduled-payment flow.
         *
         * The idempotency header is optional but recommended so repeated requests do
         * not double-charge when clients retry.
         *
         * @param id scheduled payment id.
         * @param invoiceId invoice id to pay now.
         * @param idempotencyKey optional key for safe client retries.
         * @param userDetails authenticated user context.
         * @return updated invoice payment state.
         */
    public ResponseEntity<BillerInvoiceResponse> payInvoiceNow(
            @PathVariable Long id,
            @PathVariable Long invoiceId,
                        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                                scheduledBillPaymentService.payInvoiceNow(id, invoiceId, userDetails.getUsername(), idempotencyKey));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('CUSTOMER')")
        /**
         * Returns the auto-pay execution history for a scheduled payment.
         *
         * @param id scheduled payment id.
         * @param userDetails authenticated user context.
         * @return chronological auto-pay history entries.
         */
    public ResponseEntity<List<AutoPayHistoryItemResponse>> getAutoPayHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                scheduledBillPaymentService.getAutoPayHistory(id, userDetails.getUsername()));
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    /**
        * Triggers the auto-pay batch manually for operational support.
        *
        * This is mainly for admin/teller troubleshooting and controlled reruns.
        *
        * @return confirmation message that manual processing was invoked.
     */
    public ResponseEntity<Map<String, String>> triggerAutoPayNow() {
        scheduledBillPaymentService.executeScheduledPaymentsManually();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Auto-pay processing triggered");
        return ResponseEntity.ok(response);
    }
}
