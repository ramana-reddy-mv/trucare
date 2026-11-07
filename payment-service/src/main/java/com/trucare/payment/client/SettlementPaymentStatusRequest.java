package com.trucare.payment.client;

public record SettlementPaymentStatusRequest(String status, String paymentReference, String remarks) {
}
