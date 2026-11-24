package com.trucare.claims.config;

import com.trucare.claims.exception.ClaimNotFoundException;
import com.trucare.claims.exception.MemberNotFoundException;
import com.trucare.claims.exception.PolicyNotFoundException;
import com.trucare.claims.exception.PolicyEligibilityException;
import com.trucare.claims.exception.InvalidClaimAmountException;
import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.claims.exception.ProviderNotFoundException;
import com.trucare.claims.exception.ResourceNotFoundException;
import com.trucare.shared.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(basePackages = "com.trucare.claims")
public class ClaimExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ClaimExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ClaimErrorResponse> handleInvalidClaim(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ValidationIssue> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationIssue(error.getField(), safeMessage(error.getDefaultMessage())))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Claim request validation failed", request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ClaimErrorResponse> handleUnreadableRequest(
            Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request body or required request data is invalid", request, List.of());
    }

    @ExceptionHandler({ClaimNotFoundException.class, MemberNotFoundException.class,
            ProviderNotFoundException.class, PolicyNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ClaimErrorResponse> handleNotFound(
            RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({PolicyEligibilityException.class, InvalidClaimAmountException.class})
    public ResponseEntity<ClaimErrorResponse> handleBusinessRule(
            RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidClaimTransitionException.class)
    public ResponseEntity<ClaimErrorResponse> handleInvalidTransition(
            InvalidClaimTransitionException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ClaimErrorResponse> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            status = status == HttpStatus.SERVICE_UNAVAILABLE ? status : HttpStatus.INTERNAL_SERVER_ERROR;
            String message = status == HttpStatus.SERVICE_UNAVAILABLE
                    ? "A required service is temporarily unavailable" : "An unexpected error occurred";
            return response(status, status == HttpStatus.SERVICE_UNAVAILABLE ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR",
                    message, request, List.of());
        }
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        String code = switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_ENTITY -> "BUSINESS_RULE_VIOLATION";
            default -> "REQUEST_FAILED";
        };
        return response(status, code, message, request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ClaimErrorResponse> handlePersistenceConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Claim persistence constraint rejected a request", exception);
        return response(HttpStatus.CONFLICT, "CLAIM_CONFLICT",
                "Claim conflicts with an existing record", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ClaimErrorResponse> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ClaimErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClaimErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Unexpected Claim Service request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ClaimErrorResponse> response(HttpStatus status, String code, String message,
                                                          HttpServletRequest request,
                                                          List<ValidationIssue> validationErrors) {
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = org.slf4j.MDC.get(CorrelationId.MDC_KEY);
        }
        return ResponseEntity.status(status).body(new ClaimErrorResponse(
                Instant.now(), status.value(), code, message, request.getRequestURI(), correlationId,
                validationErrors));
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value" : message;
    }

    public record ClaimErrorResponse(Instant timestamp, int status, String errorCode, String message,
                                     String path, String correlationId,
                                     List<ValidationIssue> validationErrors) { }

    public record ValidationIssue(String field, String message) { }
}
