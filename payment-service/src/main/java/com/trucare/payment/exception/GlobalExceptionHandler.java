package com.trucare.payment.exception;

import feign.FeignException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiError> payment(PaymentException exception) {
        return error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> settlementUnavailable(FeignException exception) {
        if (exception.status() == 404) {
            return error(HttpStatus.NOT_FOUND, "Settlement was not found");
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Settlement Service is unavailable");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "Payment conflicts with an existing payment or transaction reference");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> concurrent(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "Payment was modified concurrently; reload and retry");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message));
    }

    public record ApiError(Instant timestamp, int status, String message) {
    }
}
