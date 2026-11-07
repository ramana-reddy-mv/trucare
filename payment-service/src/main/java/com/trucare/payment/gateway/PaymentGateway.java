package com.trucare.payment.gateway;

import com.trucare.payment.model.Payment;

public interface PaymentGateway {
    PaymentGatewayResult process(Payment payment, String transactionReference, String providerIdempotencyKey);
    PaymentGatewayResult query(String providerReference);
}
