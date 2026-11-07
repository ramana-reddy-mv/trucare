package com.trucare.claims.client;

import java.math.BigDecimal;

public record PolicyEligibilityResponse(
        Boolean eligible,
        Long policyId,
        String policyNumber,
        String memberId,
        BigDecimal coverageAmount,
        BigDecimal availableCoverageAmount,
        String treatmentType,
        BigDecimal maximumEligibleAmount,
        String reason) {
}
