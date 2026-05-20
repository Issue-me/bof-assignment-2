package com.bof.banking.service;

import com.bof.banking.dto.billpayment.BillerInvoiceResponse;

import java.util.List;

/**
 * Encapsulates business rules for b il le ri nv oi ce se rv ic e and keeps controller logic thin.
 */
public interface BillerInvoiceService {

    List<BillerInvoiceResponse> createDummyFeaInvoices(String customerReference, Integer invoiceYear);
}
