package com.bof.banking.controller;

import com.bof.banking.dto.billpayment.BillPaymentRequest;
import com.bof.banking.dto.billpayment.BillPaymentResponse;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.service.BillPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for bill payment endpoints.
 * <p>
 * RBAC: Only CUSTOMERS can pay bills per Role Access Matrix.
 * </p>
 */
@RestController
@RequestMapping("/api/bill-payments")
@RequiredArgsConstructor
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Creates a new bill payment for the logged-in customer.
     *
     * The request is validated before it reaches the service layer. If everything
     * is valid, the payment is created and returned with HTTP 201.
     *
     * @param userDetails authenticated user context used to resolve the customer identity.
     * @param request bill payment details such as account, biller, amount, and schedule fields.
     * @return the created bill payment payload.
     */
    public ResponseEntity<BillPaymentResponse> createBillPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BillPaymentRequest request) {
        BillPaymentResponse response = billPaymentService.createBillPayment(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Returns a single bill payment by internal id.
     *
     * This is primarily used when the UI needs a detail view for one payment.
     *
     * @param userDetails authenticated user context.
     * @param id id of the bill payment to fetch.
     * @return the matching bill payment details.
     */
    public ResponseEntity<BillPaymentResponse> getBillPaymentById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(billPaymentService.getBillPaymentById(id));
    }

    @GetMapping("/reference/{paymentReference}")
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Returns a bill payment by its business reference number.
     *
     *
     * @param userDetails authenticated user context.
     * @param paymentReference external payment reference shown to users.
     * @return the matching bill payment details.
     */
    public ResponseEntity<BillPaymentResponse> getBillPaymentByReference(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String paymentReference) {
        return ResponseEntity.ok(billPaymentService.getBillPaymentByReference(paymentReference));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Lists all bill payments for the authenticated customer.
     *
     * This endpoint returns the non-paginated list used by views that need the
     * full customer payment history.
     *
     * @param userDetails authenticated user context used to scope the query.
     * @return customer bill payments ordered by service defaults.
     */
    public ResponseEntity<List<BillPaymentResponse>> getMyBillPayments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(billPaymentService.getBillPaymentsByUser(userDetails.getUsername()));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Lists customer bill payments using pageable query parameters.
     *
     * Use this endpoint when the client needs server-side pagination and sorting.
     *
     * @param userDetails authenticated user context used to scope the query.
     * @param pageable page, size, and sort settings from the request.
     * @return a page of bill payments for the customer.
     */
    public ResponseEntity<Page<BillPaymentResponse>> getMyBillPaymentsPaged(
            @AuthenticationPrincipal UserDetails userDetails, Pageable pageable) {
        return ResponseEntity.ok(billPaymentService.getBillPaymentsByUser(userDetails.getUsername(), pageable));
    }

    @GetMapping("/monitoring")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Returns a pageable monitoring view of bill payments for teller operations.
     *
     * All filter arguments are optional and can be combined to narrow results by
     * customer/biller text, biller id, status, date window, and amount range.
     *
     * @param search free-text search term for monitoring screens.
     * @param billerId optional biller id filter.
     * @param status optional payment status filter.
     * @param fromDate optional lower bound for payment date/time.
     * @param toDate optional upper bound for payment date/time.
     * @param minAmount optional minimum amount filter.
     * @param maxAmount optional maximum amount filter.
     * @param pageable page, size, and sort settings from the request.
     * @return a filtered page of bill payment records for monitoring.
     */
    public ResponseEntity<Page<BillPaymentResponse>> getBillPaymentsForMonitoring(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long billerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            Pageable pageable) {
        return ResponseEntity.ok(
                billPaymentService.getBillPaymentsForMonitoring(
                        search,
                        billerId,
                        status,
                        fromDate,
                        toDate,
                        minAmount,
                        maxAmount,
                        pageable));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    /**
     * Cancels a customer bill payment when cancellation rules allow it.
     *
     * The service decides whether the payment can still be cancelled based on
     * its current state and schedule.
     *
     * @param userDetails authenticated user context.
     * @param id id of the payment to cancel.
     * @return the updated bill payment after cancellation.
     */
    public ResponseEntity<BillPaymentResponse> cancelBillPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(billPaymentService.cancelBillPayment(id));
    }
}
