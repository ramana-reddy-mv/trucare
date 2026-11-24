package com.trucare.shared.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateClaimDTO {
    @NotBlank(message = "memberId is required")
    @Size(max = 128, message = "memberId must be at most 128 characters")
    private String memberId;

    @NotBlank(message = "providerId is required")
    @Size(max = 128, message = "providerId must be at most 128 characters")
    private String providerId;

    @NotBlank(message = "policyNumber is required")
    @Size(max = 128, message = "policyNumber must be at most 128 characters")
    private String policyNumber;

    @NotBlank(message = "treatmentType is required")
    @Pattern(regexp = "HOSPITALIZATION|SURGERY|MATERNITY|OUTPATIENT|EMERGENCY|DIAGNOSTICS|DENTAL",
            message = "treatmentType must be a supported treatment type")
    private String treatmentType;

    @NotNull(message = "treatmentDate is required")
    private LocalDate treatmentDate;

    @NotNull(message = "claimAmount is required")
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal claimAmount;
}
