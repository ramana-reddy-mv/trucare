package com.trucare.policy.dto;

import com.trucare.policy.model.TreatmentType;

import java.math.BigDecimal;

public record EligibilityCheckResponse(
        boolean eligible,
        Long policyId,
        String policyNumber,
        String memberId,
        BigDecimal coverageAmount,
        BigDecimal availableCoverageAmount,
        TreatmentType treatmentType,
        BigDecimal maximumEligibleAmount,
        String reason) {
}
