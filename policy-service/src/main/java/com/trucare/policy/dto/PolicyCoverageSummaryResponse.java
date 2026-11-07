package com.trucare.policy.dto;

import java.math.BigDecimal;
import java.util.List;

public record PolicyCoverageSummaryResponse(
        Long policyId,
        String policyNumber,
        BigDecimal coverageAmount,
        BigDecimal availableCoverageAmount,
        List<PolicyCoverageResponse> treatments) {
}
