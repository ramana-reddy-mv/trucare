package com.trucare.payment.dto;

import com.trucare.payment.model.PaymentTransactionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PaymentTransactionResponse {
    private Long id;
    private String transactionReference;
    private String providerReference;
    private BigDecimal amount;
    private PaymentTransactionStatus transactionStatus;
    private int attemptNumber;
    private String failureCode;
    private String failureReason;
    private Instant requestTimestamp;
    private Instant responseTimestamp;
    private Instant createdAt;
}
