package com.trucare.settlement.exception;

public class ClaimServiceUnavailableException extends RuntimeException {
    public ClaimServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
