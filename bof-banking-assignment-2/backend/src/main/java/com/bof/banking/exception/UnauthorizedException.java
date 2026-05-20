package com.bof.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user attempts an unauthorized action.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    /**
     * Creates a new UnauthorizedException instance.
     * @param message the message.
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Creates a new UnauthorizedException instance.
     * @param message the message.
     * @param cause the cause.
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
