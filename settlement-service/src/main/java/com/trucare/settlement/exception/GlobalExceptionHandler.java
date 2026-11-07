package com.trucare.settlement.exception;

import feign.FeignException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SettlementException.class)
    public ResponseEntity<ApiError> handleSettlement(SettlementException exception) {
        return error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(ClaimServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleClaimService(ClaimServiceUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Claim eligibility could not be verified");
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeign(FeignException exception) {
        if (exception.status() == 404) {
            return error(HttpStatus.NOT_FOUND, "Claim was not found");
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Claim eligibility could not be verified");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "A settlement already exists for this claim or a unique value conflicts");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConcurrentUpdate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "Settlement was modified concurrently; reload and retry");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        return error(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message));
    }

    public record ApiError(Instant timestamp, int status, String message) {
    }
}
