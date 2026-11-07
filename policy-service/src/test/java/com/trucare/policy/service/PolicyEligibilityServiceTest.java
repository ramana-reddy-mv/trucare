package com.trucare.policy.service;

import com.trucare.policy.dto.EligibilityCheckRequest;
import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyCoverage;
import com.trucare.policy.model.PolicyMember;
import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.model.TreatmentType;
import com.trucare.policy.repository.PolicyMemberRepository;
import com.trucare.policy.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PolicyEligibilityServiceTest {

    private PolicyRepository policyRepository;
    private PolicyMemberRepository policyMemberRepository;
    private PolicyEligibilityService service;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMemberRepository = mock(PolicyMemberRepository.class);
        service = new PolicyEligibilityService(policyRepository, policyMemberRepository);
    }

    @Test
    void activeCoveredPolicyIsEligibleAndCheckIsReadOnly() {
        arrangeActivePolicy();
        var response = service.checkEligibility(request(new BigDecimal("150000")));
        assertTrue(response.eligible());
        assertEquals(new BigDecimal("300000"), response.maximumEligibleAmount());
        verify(policyRepository, never()).save(any());
        verify(policyMemberRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownPolicy() {
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.empty());
        var response = service.checkEligibility(request(new BigDecimal("100")));
        assertFalse(response.eligible());
        assertEquals("Policy not found", response.reason());
    }

    @Test
    void rejectsInactivePolicy() {
        Policy policy = policy();
        policy.setStatus(PolicyStatus.INACTIVE);
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.of(policy));
        var response = service.checkEligibility(request(new BigDecimal("100")));
        assertFalse(response.eligible());
        assertEquals("Policy status is INACTIVE", response.reason());
    }

    @Test
    void rejectsExpiredOrOutOfPeriodPolicy() {
        Policy policy = policy();
        policy.setStatus(PolicyStatus.EXPIRED);
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.of(policy));
        assertEquals("Policy status is EXPIRED",
                service.checkEligibility(request(new BigDecimal("100"))).reason());

        policy.setStatus(PolicyStatus.ACTIVE);
        var outOfPeriod = service.checkEligibility(new EligibilityCheckRequest(
                "M123456", "POL1001", TreatmentType.HOSPITALIZATION,
                LocalDate.parse("2027-01-01"), new BigDecimal("100")));
        assertEquals("Treatment date is after the policy end date", outOfPeriod.reason());
    }

    @Test
    void rejectsMemberNotEnrolled() {
        arrangeActivePolicy();
        when(policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                1001L, "M123456", EnrollmentStatus.ACTIVE)).thenReturn(Optional.empty());
        var response = service.checkEligibility(request(new BigDecimal("100")));
        assertFalse(response.eligible());
        assertEquals("Member is not actively enrolled in this policy", response.reason());
    }

    @Test
    void rejectsUncoveredTreatment() {
        Policy policy = policy();
        policy.getCoverages().clear();
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.of(policy));
        when(policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                1001L, "M123456", EnrollmentStatus.ACTIVE)).thenReturn(Optional.of(enrollment(
                LocalDate.parse("2026-01-01"))));
        var response = service.checkEligibility(request(new BigDecimal("100")));
        assertEquals("Treatment type is not covered under this policy", response.reason());
    }

    @Test
    void rejectsIncompleteWaitingPeriod() {
        arrangeActivePolicy();
        when(policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                1001L, "M123456", EnrollmentStatus.ACTIVE)).thenReturn(Optional.of(enrollment(
                LocalDate.parse("2026-10-15"))));
        var response = service.checkEligibility(request(new BigDecimal("100")));
        assertEquals("Applicable waiting period has not been completed", response.reason());
    }

    @Test
    void rejectsInsufficientAvailableCoverage() {
        Policy policy = policy();
        policy.setAvailableCoverageAmount(new BigDecimal("100000"));
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.of(policy));
        when(policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                1001L, "M123456", EnrollmentStatus.ACTIVE)).thenReturn(Optional.of(enrollment(
                LocalDate.parse("2026-01-01"))));
        var response = service.checkEligibility(request(new BigDecimal("150000")));
        assertFalse(response.eligible());
        assertEquals("Available policy coverage is insufficient", response.reason());
        assertEquals(new BigDecimal("100000"), response.maximumEligibleAmount());
    }

    @Test
    void rejectsAmountAboveTreatmentLimit() {
        arrangeActivePolicy();
        var response = service.checkEligibility(request(new BigDecimal("350000")));
        assertFalse(response.eligible());
        assertEquals("Requested amount exceeds the treatment coverage limit", response.reason());
    }

    private void arrangeActivePolicy() {
        when(policyRepository.findWithCoveragesByPolicyNumber("POL1001")).thenReturn(Optional.of(policy()));
        when(policyMemberRepository.findByPolicyIdAndMemberIdAndStatus(
                1001L, "M123456", EnrollmentStatus.ACTIVE)).thenReturn(Optional.of(enrollment(
                LocalDate.parse("2026-01-01"))));
    }

    private EligibilityCheckRequest request(BigDecimal amount) {
        return new EligibilityCheckRequest(
                "M123456",
                "POL1001",
                TreatmentType.HOSPITALIZATION,
                LocalDate.parse("2026-11-07"),
                amount);
    }

    private Policy policy() {
        Policy policy = new Policy();
        policy.setId(1001L);
        policy.setPolicyNumber("POL1001");
        policy.setCoverageAmount(new BigDecimal("500000"));
        policy.setAvailableCoverageAmount(new BigDecimal("400000"));
        policy.setStartDate(LocalDate.parse("2026-01-01"));
        policy.setEndDate(LocalDate.parse("2026-12-31"));
        policy.setStatus(PolicyStatus.ACTIVE);

        PolicyCoverage coverage = new PolicyCoverage();
        coverage.setPolicy(policy);
        coverage.setTreatmentType(TreatmentType.HOSPITALIZATION);
        coverage.setCoverageLimit(new BigDecimal("300000"));
        coverage.setWaitingPeriodDays(30);
        policy.setCoverages(new java.util.ArrayList<>(List.of(coverage)));
        return policy;
    }

    private PolicyMember enrollment(LocalDate enrollmentDate) {
        PolicyMember enrollment = new PolicyMember();
        enrollment.setMemberId("M123456");
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrollmentDate(enrollmentDate);
        return enrollment;
    }
}
