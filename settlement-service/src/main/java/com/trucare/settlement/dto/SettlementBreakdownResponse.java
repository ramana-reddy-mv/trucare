package com.trucare.settlement.dto;

import com.trucare.settlement.model.ComponentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class SettlementBreakdownResponse {
    private Long id;
    private String description;
    private ComponentType componentType;
    private BigDecimal amount;
    private Instant createdAt;
}
