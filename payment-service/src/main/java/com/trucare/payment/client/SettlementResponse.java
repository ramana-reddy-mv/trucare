package com.trucare.payment.client;

import java.math.BigDecimal;

public record SettlementResponse(
        Long id,
        String settlementNumber,
        String claimId,
        String memberId,
        Long policyId,
        String settlementType,
        BigDecimal payableAmount,
        String currency,
        String settlementStatus,
        String recipientType,
        String recipientId) {
}
