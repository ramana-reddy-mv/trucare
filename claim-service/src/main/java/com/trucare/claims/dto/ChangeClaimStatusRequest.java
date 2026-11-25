package com.trucare.claims.dto;

import com.trucare.claims.model.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeClaimStatusRequest(
        @NotNull ClaimStatus nextStatus,
        @Size(max = 1000) String reason) {
}
