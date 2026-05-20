package com.bof.banking.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9\\s-]{7,20}$"
    );

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
            "^BOF[A-Z0-9]{8}$"
    );

    private ValidationUtil() {
        // Utility class - no instantiation
    }

    /**
     * Checks whether valid email is valid.
     * @param email the email of the authenticated user.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Checks whether valid phone number is valid.
     * @param phoneNumber the phone Number.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber == null || phoneNumber.isEmpty() ||
                PHONE_PATTERN.matcher(phoneNumber).matches();
    }

    /**
     * Checks whether valid account number is valid.
     * @param accountNumber the account Number.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isValidAccountNumber(String accountNumber) {
        return accountNumber != null && ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches();
    }

    /**
     * Checks whether positive amount is valid.
     * @param amount the monetary value used by this operation.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isPositiveAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks whether non negative amount is valid.
     * @param amount the monetary value used by this operation.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isNonNegativeAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Checks whether valid password is valid.
     * @param password the credential or security value provided by the caller.
     * @return true if the condition is met; otherwise false.
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    /**
     * Handles sanitize input.
     * @param input the input.
     * @return the resulting text value.
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        return input.trim()
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#x27;");
    }
}
