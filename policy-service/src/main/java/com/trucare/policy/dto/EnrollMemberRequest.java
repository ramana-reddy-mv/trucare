package com.trucare.policy.dto;

import com.trucare.policy.model.Relationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EnrollMemberRequest(
        @NotBlank @Size(max = 64) String memberId,
        @NotNull Relationship relationship,
        @NotNull LocalDate enrollmentDate) {
}
