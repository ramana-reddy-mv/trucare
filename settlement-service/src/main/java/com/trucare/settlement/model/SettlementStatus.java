package com.trucare.settlement.model;

public enum SettlementStatus {
    PENDING,
    CALCULATED,
    READY_FOR_PAYMENT,
    PAYMENT_PROCESSING,
    SETTLED,
    FAILED,
    CANCELLED
}
