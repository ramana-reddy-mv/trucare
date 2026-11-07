package com.trucare.policy.dto;

import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.model.PolicyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PolicyResponse(
        Long id,
        String policyNumber,
        String policyName,
        PolicyType policyType,
        BigDecimal coverageAmount,
        BigDecimal availableCoverageAmount,
        LocalDate startDate,
        LocalDate endDate,
        PolicyStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<PolicyCoverageResponse> coverages) {
}
