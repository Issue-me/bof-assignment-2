package com.bof.banking.model.enums;

/**
 * Frequency of scheduled bill payment execution.
 */
public enum ScheduleFrequency {
    ONCE,           // One-time payment on the start date
    WEEKLY,         // Every 7 days
    BIWEEKLY,       // Every 14 days
    MONTHLY,        // Same day each month
    QUARTERLY,      // Every 3 months
    ANNUALLY        // Yearly
}
