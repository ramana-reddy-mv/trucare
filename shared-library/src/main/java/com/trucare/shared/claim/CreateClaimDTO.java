package com.trucare.shared.claim;

import com.trucare.shared.document.DocumentDTO;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateClaimDTO {

    private String claimId;
    @NotBlank
    private String memberId;
    @NotBlank
    private String providerId;
    @NotBlank
    private String policyNumber;
    @NotBlank
    private String treatmentType;
    @NotNull
    private LocalDate treatmentDate;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal claimAmount;
    private String claimStatuses; // Relationship to ClaimStatusDTO
    private LocalDateTime createdAt;  // Claim creation date
    private LocalDateTime updatedAt;
    private List<Long> documentIds;
}
