package com.bof.banking.service.impl;

import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.InsufficientFundsException;
import com.bof.banking.service.InterestService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Default implementation for interest and fee calculations.
 */
@Service
public class InterestServiceImpl implements InterestService {

    private static final BigDecimal NRWHT_RATE = new BigDecimal("0.10");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");

    @Override
    /**
     * Handles calculate savings interest.
     * @param balance the monetary value used by this operation.
     * @param annualRate the monetary value used by this operation.
     * @return the result of the operation.
     */
    public BigDecimal calculateSavingsInterest(BigDecimal balance, BigDecimal annualRate) {
        if (balance == null || annualRate == null) {
            return BigDecimal.ZERO;
        }

        if (balance.compareTo(BigDecimal.ZERO) < 0 || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Balance and annual rate must be non-negative");
        }

        return balance
                .multiply(annualRate)
                .divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    @Override
    /**
     * Handles calculate nrwht tax.
     * @param grossInterest the monetary value used by this operation.
     * @return the result of the operation.
     */
    public BigDecimal calculateNrwhtTax(BigDecimal grossInterest) {
        if (grossInterest == null || grossInterest.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (grossInterest.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Gross interest must be non-negative");
        }

        return grossInterest
                .multiply(NRWHT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    /**
     * Updates monthly maintenance fee values.
     * @param balance the monetary value used by this operation.
     * @param feeAmount the monetary value used by this operation.
     * @return the result of the operation.
     */
    public BigDecimal applyMonthlyMaintenanceFee(BigDecimal balance, BigDecimal feeAmount) {
        if (balance == null || feeAmount == null) {
            throw new BadRequestException("Balance and fee amount are required");
        }

        if (balance.compareTo(BigDecimal.ZERO) < 0 || feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Balance and fee amount must be non-negative");
        }

        if (balance.compareTo(feeAmount) < 0) {
            throw new InsufficientFundsException("Insufficient funds to apply monthly maintenance fee");
        }

        return balance.subtract(feeAmount);
    }
}
