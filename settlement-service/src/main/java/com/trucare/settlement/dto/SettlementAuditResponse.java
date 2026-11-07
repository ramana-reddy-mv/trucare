package com.trucare.settlement.dto;

import com.trucare.settlement.model.SettlementStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class SettlementAuditResponse {
    private Long id;
    private SettlementStatus previousStatus;
    private SettlementStatus newStatus;
    private String action;
    private String remarks;
    private String performedBy;
    private Instant createdAt;
}
