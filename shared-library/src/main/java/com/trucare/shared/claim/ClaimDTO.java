package com.trucare.shared.claim;

import com.trucare.shared.document.DocumentDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimDTO {
    private Long id;
    private String claimId;
    private String memberId; // Can be expanded to MemberDTO if needed
    private String providerId; // Can be expanded to ProviderDTO if needed
    private String policyNumber;
    private String treatmentType;
    private LocalDate treatmentDate;
    private BigDecimal claimAmount;
    private String claimStatuses; // Relationship to ClaimStatusDTO
    private LocalDateTime createdAt;  // Claim creation date
    private LocalDateTime updatedAt;
    private List<DocumentDTO> documents; // Relationship to a list of DocumentDTOs
}
