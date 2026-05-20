package com.bof.banking.service;

import java.math.BigDecimal;

/**
 * Interest calculation service for account business rules.
 */
public interface InterestService {

    /**
     * Calculates monthly savings interest based on annual interest rate.
     */
    BigDecimal calculateSavingsInterest(BigDecimal balance, BigDecimal annualRate);

    /**
     * Calculates 10% NRWHT tax from a gross interest amount.
     */
    BigDecimal calculateNrwhtTax(BigDecimal grossInterest);

    /**
     * Applies monthly maintenance fee to a balance and returns updated balance.
     */
    BigDecimal applyMonthlyMaintenanceFee(BigDecimal balance, BigDecimal feeAmount);
}
