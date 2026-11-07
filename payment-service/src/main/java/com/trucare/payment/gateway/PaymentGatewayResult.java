package com.trucare.payment.gateway;

public record PaymentGatewayResult(
        GatewayOutcome outcome,
        String providerReference,
        String failureCode,
        String failureReason) {
}
