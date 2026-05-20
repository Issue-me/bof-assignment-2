package com.bof.banking.repository;

import com.bof.banking.model.Biller;
import com.bof.banking.model.BillerInvoice;
import com.bof.banking.model.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for biller invoice persistence and monthly invoice lookups.
 */
@Repository
public interface BillerInvoiceRepository extends JpaRepository<BillerInvoice, Long> {

    Optional<BillerInvoice> findByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
            Biller biller,
            String customerReference,
            Integer invoiceMonth,
            Integer invoiceYear);

    Optional<BillerInvoice> findByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYearAndStatus(
            Biller biller,
            String customerReference,
            Integer invoiceMonth,
            Integer invoiceYear,
            InvoiceStatus status);

    List<BillerInvoice> findByBillerAndCustomerReferenceOrderByInvoiceYearAscInvoiceMonthAsc(
            Biller biller,
            String customerReference);

    List<BillerInvoice> findByCustomerReferenceOrderByInvoiceYearDescInvoiceMonthDesc(String customerReference);

    boolean existsByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
            Biller biller,
            String customerReference,
            Integer invoiceMonth,
            Integer invoiceYear);
}
