package com.bof.banking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Externalized configuration properties for banking operations.
 * <p>
 * This class centralizes all configurable banking parameters to avoid
 * hard-coding values throughout the codebase. All values can be overridden
 * via application.properties or environment variables.
 * </p>
 *
 * <p>Example usage in application.properties:</p>
 * <pre>
 * bof.interest-rate.savings=0.025
 * bof.interest-rate.fixed-deposit=0.045
 * bof.customer-id-prefix=BOF
 * </pre>
 *
 * @author Bank of Fiji Development Team
 * @version 1.0.0
 * @since 2026-03-06
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bof")
public class BankingProperties {

    /**
     * Interest rate configuration for different account types.
     */
    private InterestRate interestRate = new InterestRate();

    /**
     * Prefix used for generating customer IDs (e.g., "BOF" generates "BOF-000001").
     */
    private String customerIdPrefix = "BOF";

    /**
     * Prefix used for generating account numbers.
     */
    private String accountNumberPrefix = "BOF";

    /**
     * Minimum password length for user registration.
     */
    private int minPasswordLength = 8;

    /**
     * Whether to enable demo data seeding on startup.
     */
    private boolean seedDemoData = true;

    /**
     * Interest rate configuration holder.
     */
    @Data
    public static class InterestRate {
        /**
         * Annual interest rate for savings accounts (default: 2.5%).
         */
        private BigDecimal savings = new BigDecimal("0.025");

        /**
         * Annual interest rate for checking accounts (default: 0%).
         */
        private BigDecimal checking = BigDecimal.ZERO;

        /**
         * Annual interest rate for fixed deposit accounts (default: 4.5%).
         */
        private BigDecimal fixedDeposit = new BigDecimal("0.045");

        /**
         * Annual interest rate for business accounts (default: 1.5%).
         */
        private BigDecimal business = new BigDecimal("0.015");
    }
}
