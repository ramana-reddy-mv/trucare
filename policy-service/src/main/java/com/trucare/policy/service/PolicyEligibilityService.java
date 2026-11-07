package com.trucare.policy.service;

import com.trucare.policy.dto.EligibilityCheckRequest;
import com.trucare.policy.dto.EligibilityCheckResponse;
import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyCoverage;
import com.trucare.policy.model.PolicyMember;
import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.repository.PolicyMemberRepository;
import com.trucare.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class PolicyEligibilityService {

    private final PolicyRepository policyRepository;
    private final PolicyMemberRepository policyMemberRepository;

    public PolicyEligibilityService(
            PolicyRepository policyRepository,
            PolicyMemberRepository policyMemberRepository) {
        this.policyRepository = policyRepository;
        this.policyMemberRepository = policyMemberRepository;
    }

    @Transactional(readOnly = true)
    public EligibilityCheckResponse checkEligibility(EligibilityCheckRequest request) {
        Policy policy = policyRepository.findWithCoveragesByPolicyNumber(request.policyNumber())
                .orElse(null);
        if (policy == null) {
            return rejected(null, request, BigDecimal.ZERO, "Policy not found");
        }
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            return rejected(policy, request, null, "Policy status is " + policy.getStatus());
        }
        if (request.treatmentDate().isBefore(policy.getStartDate())) {
            return rejected(policy, request, null, "Treatment date is before the policy start date");
        }
        if (request.treatmentDate().isAfter(policy.getEndDate())) {
            return rejected(policy, request, null, "Treatment date is after the policy end date");
        }

        PolicyMember enrollment = policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                        policy.getId(), request.memberId(), EnrollmentStatus.ACTIVE)
                .orElse(null);
        if (enrollment == null) {
            return rejected(policy, request, null,
                    "Member is not actively enrolled in this policy");
        }

        PolicyCoverage coverage = policy.getCoverages().stream()
                .filter(item -> item.getTreatmentType() == request.treatmentType())
                .findFirst()
                .orElse(null);
        if (coverage == null) {
            return rejected(policy, request, null,
                    "Treatment type is not covered under this policy");
        }
        if (request.treatmentDate().isBefore(enrollment.getEnrollmentDate())) {
            return rejected(policy, request,
                    coverage.getCoverageLimit().min(policy.getAvailableCoverageAmount()),
                    "Treatment occurred before the member's enrollment date");
        }
        LocalDate waitingPeriodEnds = enrollment.getEnrollmentDate()
                .plusDays(coverage.getWaitingPeriodDays());
        if (request.treatmentDate().isBefore(waitingPeriodEnds)) {
            return rejected(policy, request,
                    coverage.getCoverageLimit().min(policy.getAvailableCoverageAmount()),
                    "Applicable waiting period has not been completed");
        }

        BigDecimal maximumEligibleAmount = coverage.getCoverageLimit()
                .min(policy.getAvailableCoverageAmount());
        if (request.requestedAmount().compareTo(coverage.getCoverageLimit()) > 0) {
            return rejected(policy, request, maximumEligibleAmount,
                    "Requested amount exceeds the treatment coverage limit");
        }
        if (request.requestedAmount().compareTo(policy.getAvailableCoverageAmount()) > 0) {
            return rejected(policy, request, maximumEligibleAmount,
                    "Available policy coverage is insufficient");
        }
        return new EligibilityCheckResponse(
                true,
                policy.getId(),
                policy.getPolicyNumber(),
                request.memberId(),
                policy.getCoverageAmount(),
                policy.getAvailableCoverageAmount(),
                request.treatmentType(),
                maximumEligibleAmount,
                "Policy is active and treatment is covered");
    }

    private EligibilityCheckResponse rejected(
            Policy policy,
            EligibilityCheckRequest request,
            BigDecimal maximumEligibleAmount,
            String reason) {
        return new EligibilityCheckResponse(
                false,
                policy == null ? null : policy.getId(),
                policy == null ? request.policyNumber() : policy.getPolicyNumber(),
                request.memberId(),
                policy == null ? null : policy.getCoverageAmount(),
                policy == null ? null : policy.getAvailableCoverageAmount(),
                request.treatmentType(),
                maximumEligibleAmount,
                reason);
    }
}
