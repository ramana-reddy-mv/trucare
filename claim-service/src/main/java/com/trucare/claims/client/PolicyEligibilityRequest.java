package com.trucare.claims.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyEligibilityRequest(
        String memberId,
        String policyNumber,
        String treatmentType,
        LocalDate treatmentDate,
        BigDecimal requestedAmount) {
}
