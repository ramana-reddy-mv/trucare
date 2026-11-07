package com.trucare.policy.dto;

import com.trucare.policy.model.TreatmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PolicyCoverageRequest(
        @NotNull TreatmentType treatmentType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal coverageLimit,
        @NotNull @Min(0) Integer waitingPeriodDays,
        String description) {
}
