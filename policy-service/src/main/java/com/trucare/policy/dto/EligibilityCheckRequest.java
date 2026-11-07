package com.trucare.policy.dto;

import com.trucare.policy.model.TreatmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EligibilityCheckRequest(
        @NotBlank @Size(max = 64) String memberId,
        @NotBlank @Size(max = 64) String policyNumber,
        @NotNull TreatmentType treatmentType,
        @NotNull LocalDate treatmentDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal requestedAmount) {
}
