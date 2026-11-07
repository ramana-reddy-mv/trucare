package com.trucare.settlement.dto;

import com.trucare.settlement.model.RecipientType;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.model.SettlementType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class SettlementResponse {
    private Long id;
    private String settlementNumber;
    private String claimId;
    private String memberId;
    private String providerId;
    private Long policyId;
    private SettlementType settlementType;
    private BigDecimal approvedClaimAmount;
    private BigDecimal deductibleAmount;
    private BigDecimal copayAmount;
    private BigDecimal nonPayableAmount;
    private BigDecimal payableAmount;
    private String currency;
    private SettlementStatus settlementStatus;
    private RecipientType recipientType;
    private String recipientId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant settledAt;
    private long version;
    private List<SettlementBreakdownResponse> breakdown;
    private List<SettlementAuditResponse> auditHistory;
    private List<SettlementPaymentResponse> paymentTransactions;
}
