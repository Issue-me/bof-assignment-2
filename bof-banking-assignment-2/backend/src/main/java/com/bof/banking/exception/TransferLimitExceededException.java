package com.bof.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception raised when transfer limits are exceeded.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class TransferLimitExceededException extends RuntimeException {

    /**
     * Creates a new TransferLimitExceededException instance.
     * @param message the message.
     */
    public TransferLimitExceededException(String message) {
        super(message);
    }
}
