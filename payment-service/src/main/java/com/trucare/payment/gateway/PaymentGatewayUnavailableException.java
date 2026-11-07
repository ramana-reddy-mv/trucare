package com.trucare.payment.gateway;

public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
