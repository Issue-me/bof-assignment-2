package com.bof.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown for invalid request data.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    /**
     * Creates a new BadRequestException instance.
     * @param message the message.
     */
    public BadRequestException(String message) {
        super(message);
    }

    /**
     * Creates a new BadRequestException instance.
     * @param message the message.
     * @param cause the cause.
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
