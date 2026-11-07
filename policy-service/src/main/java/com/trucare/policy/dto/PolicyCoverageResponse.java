package com.trucare.policy.dto;

import com.trucare.policy.model.TreatmentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PolicyCoverageResponse(
        Long id,
        TreatmentType treatmentType,
        BigDecimal coverageLimit,
        Integer waitingPeriodDays,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
