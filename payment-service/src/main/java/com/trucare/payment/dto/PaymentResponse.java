package com.trucare.payment.dto;

import com.trucare.payment.model.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class PaymentResponse {
    private Long id;
    private String paymentReference;
    private Long settlementId;
    private String settlementNumber;
    private String claimId;
    private String memberId;
    private String recipientId;
    private RecipientType recipientType;
    private SettlementType settlementType;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private String providerTransactionReference;
    private String failureCode;
    private String failureReason;
    private Instant initiatedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;
    private List<PaymentTransactionResponse> transactions;
    private List<PaymentAuditResponse> auditHistory;
    private List<CallbackDeliveryResponse> callbackDeliveries;
}
