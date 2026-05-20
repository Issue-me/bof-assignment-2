package com.bof.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested resource is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a new ResourceNotFoundException instance.
     * @param message the message.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new ResourceNotFoundException instance.
     * @param message the message.
     * @param cause the cause.
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
