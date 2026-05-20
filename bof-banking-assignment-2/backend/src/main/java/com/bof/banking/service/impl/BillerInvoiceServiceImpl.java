package com.bof.banking.service.impl;

import com.bof.banking.dto.billpayment.BillerInvoiceResponse;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.Biller;
import com.bof.banking.model.BillerInvoice;
import com.bof.banking.model.enums.InvoiceStatus;
import com.bof.banking.repository.BillerInvoiceRepository;
import com.bof.banking.repository.BillerRepository;
import com.bof.banking.service.BillerInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * Encapsulates business rules for b il le ri nv oi ce se rv ic ei mp l and keeps controller logic thin.
 */
public class BillerInvoiceServiceImpl implements BillerInvoiceService {

    private final BillerRepository billerRepository;
    private final BillerInvoiceRepository billerInvoiceRepository;

    @Override
    @Transactional
    /**
     * Creates dummy fea invoices data.
     * @param customerReference the date or time value used by this operation.
     * @param invoiceYear the date or time value used by this operation.
     * @return the matching results.
     */
    public List<BillerInvoiceResponse> createDummyFeaInvoices(String customerReference, Integer invoiceYear) {
        int targetYear = invoiceYear != null ? invoiceYear : LocalDate.now().getYear();

        Biller feaBiller = billerRepository.findByBillerCodeIgnoreCase("FEA001")
                .orElseThrow(() -> new ResourceNotFoundException("FEA biller (FEA001) not found"));

        BigDecimal[] monthlyAmounts = {
                new BigDecimal("134.15"),
                new BigDecimal("142.40"),
                new BigDecimal("128.95"),
                new BigDecimal("150.10"),
                new BigDecimal("137.85"),
                new BigDecimal("166.30"),
                new BigDecimal("159.75"),
                new BigDecimal("171.20"),
                new BigDecimal("145.65"),
                new BigDecimal("138.20"),
                new BigDecimal("147.50"),
                new BigDecimal("162.90")
        };

        List<BillerInvoiceResponse> createdOrExisting = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            final int currentMonth = month;
            BillerInvoice invoice = billerInvoiceRepository
                    .findByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
                            feaBiller,
                            customerReference,
                            currentMonth,
                            targetYear)
                    .orElseGet(() -> billerInvoiceRepository.save(BillerInvoice.builder()
                            .biller(feaBiller)
                            .customerReference(customerReference)
                            .invoiceMonth(currentMonth)
                            .invoiceYear(targetYear)
                            .invoiceAmount(monthlyAmounts[currentMonth - 1])
                            .dueDate(LocalDate.of(targetYear, currentMonth, 20))
                            .status(InvoiceStatus.UNPAID)
                            .build()));

            createdOrExisting.add(BillerInvoiceResponse.builder()
                    .id(invoice.getId())
                    .billerId(invoice.getBiller().getId())
                    .billerName(invoice.getBiller().getBillerName())
                    .customerReference(invoice.getCustomerReference())
                    .invoiceMonth(invoice.getInvoiceMonth())
                    .invoiceYear(invoice.getInvoiceYear())
                    .invoiceAmount(invoice.getInvoiceAmount())
                    .dueDate(invoice.getDueDate())
                    .status(invoice.getStatus())
                    .paymentReference(invoice.getBillPayment() != null ? invoice.getBillPayment().getPaymentReference() : null)
                    .paidAt(invoice.getPaidAt())
                    .build());
        }

        return createdOrExisting;
    }
}
