package com.trucare.settlement.dto;

import com.trucare.settlement.model.SettlementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateSettlementRequest {

    @NotBlank
    private String claimId;

    @NotNull
    @Positive
    private Long policyId;

    @NotNull
    private SettlementType settlementType;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 17, fraction = 8)
    private BigDecimal deductibleAmount;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 17, fraction = 8)
    private BigDecimal copayAmount;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 17, fraction = 8)
    private BigDecimal nonPayableAmount;

    private BigDecimal approvedClaimAmount;

    private String memberId;

    private String providerId;
}
