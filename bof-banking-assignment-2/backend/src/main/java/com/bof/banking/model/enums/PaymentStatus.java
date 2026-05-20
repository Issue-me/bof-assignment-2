package com.bof.banking.model.enums;

/**
 * Status of a payment or scheduled payment.
 */
public enum PaymentStatus {
    PENDING,    // Payment is pending execution
    COMPLETED,  // Payment has been successfully completed
    FAILED,     // Payment execution failed
    CANCELLED,  // Payment has been cancelled
    ACTIVE,     // Scheduled payment is active
    PAUSED      // Scheduled payment is paused (temporarily not executing)
}
