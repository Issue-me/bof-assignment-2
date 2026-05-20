package com.bof.banking.controller;

import com.bof.banking.dto.billpayment.BillerInvoiceResponse;
import com.bof.banking.dto.billpayment.DummyInvoiceSeedRequest;
import com.bof.banking.service.BillerInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bill-payments/invoices")
@RequiredArgsConstructor
/**
 * Coordinates API workflows for biller invoice controller and maps service outcomes to HTTP responses.
 */
public class BillerInvoiceController {

    private final BillerInvoiceService billerInvoiceService;

    @PostMapping("/dummy/fea")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<List<BillerInvoiceResponse>> createDummyFeaInvoices(
            @Valid @RequestBody DummyInvoiceSeedRequest request) {
        List<BillerInvoiceResponse> response = billerInvoiceService
                .createDummyFeaInvoices(request.getCustomerReference(), request.getInvoiceYear());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
