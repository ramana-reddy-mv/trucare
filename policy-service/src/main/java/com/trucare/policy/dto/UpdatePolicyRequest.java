package com.trucare.policy.dto;

import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.model.PolicyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdatePolicyRequest(
        @NotBlank @Size(max = 64) String policyNumber,
        @NotBlank @Size(max = 160) String policyName,
        @NotNull PolicyType policyType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal coverageAmount,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull PolicyStatus status,
        @NotEmpty List<@Valid PolicyCoverageRequest> coverages) {
}
