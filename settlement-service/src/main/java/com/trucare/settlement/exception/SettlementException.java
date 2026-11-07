package com.trucare.settlement.exception;

import org.springframework.http.HttpStatus;

public class SettlementException extends RuntimeException {

    private final HttpStatus status;

    public SettlementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
