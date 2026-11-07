package com.trucare.policy.dto;

import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Relationship;

import java.time.Instant;
import java.time.LocalDate;

public record PolicyMemberResponse(
        Long id,
        Long policyId,
        String policyNumber,
        String memberId,
        Relationship relationship,
        LocalDate enrollmentDate,
        EnrollmentStatus status,
        Instant createdAt) {
}
