package com.bof.banking.model.enums;

/**
 * Supported customer notification categories.
 *
 * New types for requirement 6:
 *   INTEREST_RATE_CHANGED   — account interest rate has been updated
 *   INTEREST_RATE_UPCOMING  — advance notice of a scheduled rate change
 *   RIWT_EXEMPTION_APPLIED  — teller approved customer's RIWT exemption
 *   RIWT_EXEMPTION_REJECTED — teller rejected customer's RIWT exemption upload
 */
public enum NotificationType {
    BILL_PAYMENT_PROCESSED,
    HIGH_VALUE_TRANSACTION,
    TRANSACTION,
    SYSTEM,
    INTEREST_RATE_CHANGE,
    INTEREST_RATE_UPCOMING,
    NRWHT_REFUND_CREDITED,
    RIWT_EXEMPTION_APPLIED,
    RIWT_EXEMPTION_REJECTED,
    FRCS_TAX_SUBMITTED 
}