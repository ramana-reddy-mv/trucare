package com.trucare.payment.dto;

public record PaymentInitiationResult(PaymentResponse payment, boolean replayed) {
}
