package com.bof.banking.model.enums;

/**
 * Status lifecycle of a loan application/account.
 *
 * <pre>
 *  PENDING ──► ACTIVE  (admin approves — funds disbursed immediately)
 *  PENDING ──► REJECTED (auto-reject by DSR rules, or manual admin rejection)
 *  ACTIVE  ──► CLOSED   (loan fully repaid)
 *  ACTIVE  ──► DEFAULTED (missed repayments threshold exceeded)
 * </pre>
 *
 * Note: APPROVED is intentionally removed. Per the Bank of Fiji workflow,
 * admin approval immediately disburses funds and moves the loan to ACTIVE.
 * A separate APPROVED holding state is unnecessary and caused frontend
 * inconsistencies.
 */
public enum LoanStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    CLOSED,
    PAID_OFF,
    DEFAULTED
}