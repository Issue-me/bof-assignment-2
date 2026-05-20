package com.bof.banking.util.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link NumericPassword} annotation.
 * Validates that a password contains only numeric characters and meets minimum length.
 */
public class NumericPasswordValidator implements ConstraintValidator<NumericPassword, String> {

    private int minLength;

    @Override
    /**
     * Creates ialize data.
     * @param constraintAnnotation the constraint Annotation.
     */
    public void initialize(NumericPassword constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
    }

    @Override
    /**
     * Checks whether valid is valid.
     * @param password the credential or security value provided by the caller.
     * @param context the context.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        // Check minimum length
        if (password.length() < minLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Password must be at least " + minLength + " digits"
            ).addConstraintViolation();
            return false;
        }

        // Check that all characters are digits
        return password.matches("^[0-9]+$");
    }
}
