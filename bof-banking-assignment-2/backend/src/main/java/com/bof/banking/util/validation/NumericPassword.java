package com.bof.banking.util.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Annotation to validate that a password contains only numeric characters.
 * Used for customer passwords in mobile app authentication.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NumericPasswordValidator.class)
@Documented
public @interface NumericPassword {

    String message() default "Password must contain only numbers";

    int minLength() default 8;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
