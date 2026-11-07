package com.trucare.settlement.dto;

import com.trucare.settlement.model.PaymentTransactionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class SettlementPaymentResponse {
    private Long id;
    private String paymentReference;
    private PaymentTransactionStatus transactionStatus;
    private BigDecimal amount;
    private String currency;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;
}
