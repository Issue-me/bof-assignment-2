package com.bof.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a transaction fails due to insufficient funds.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientFundsException extends RuntimeException {

    /**
     * Creates a new InsufficientFundsException instance.
     * @param message the message.
     */
    public InsufficientFundsException(String message) {
        super(message);
    }

    /**
     * Creates a new InsufficientFundsException instance.
     * @param message the message.
     * @param cause the cause.
     */
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
