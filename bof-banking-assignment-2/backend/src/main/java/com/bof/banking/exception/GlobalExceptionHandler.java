package com.bof.banking.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    /**
     * Handles handle resource not found exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Not Found")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    /**
     * Handles handle insufficient funds exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(InsufficientFundsException ex) {
        log.error("Insufficient funds: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Insufficient Funds")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(BadRequestException.class)
    /**
     * Handles handle bad request exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Bad Request")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(TransferLimitExceededException.class)
    /**
     * Handles handle transfer limit exceeded exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleTransferLimitExceededException(TransferLimitExceededException ex) {
        log.warn("Transfer limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                        .error("Transfer Limit Exceeded")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(UnauthorizedException.class)
    /**
     * Handles handle unauthorized exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error("Unauthorized")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    /**
     * Handles handle authentication exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception ex) {
        log.error("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error("Authentication Failed")
                        .message("Invalid email or password")
                        .build());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    /**
     * Handles handle access denied exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(Exception ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error("Access Denied")
                        .message("You do not have permission to perform this action")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * Handles handle validation exceptions.
     * @param ex the ex.
     * @return the result of the operation.
     */
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(OptimisticLockException.class)
    /**
     * Handles handle optimistic lock exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error("Concurrent Update")
                        .message("Transaction conflict detected. Please retry.")
                        .build());
    }

    @ExceptionHandler(PessimisticLockException.class)
    /**
     * Handles handle pessimistic lock exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handlePessimisticLockException(PessimisticLockException ex) {
        log.warn("Pessimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.LOCKED.value())
                        .error("Account Locked")
                        .message("Account is currently locked. Please try again shortly.")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    /**
     * Handles handle generic exception.
     * @param ex the ex.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .build());
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
    }
}
