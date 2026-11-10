package com.trucare.policy.service;

import com.trucare.shared.client.MemberClient;
import com.trucare.policy.dto.CreatePolicyRequest;
import com.trucare.policy.dto.EnrollMemberRequest;
import com.trucare.policy.dto.PolicyCoverageRequest;
import com.trucare.policy.exception.ConflictException;
import com.trucare.policy.exception.MemberNotFoundException;
import com.trucare.policy.exception.MemberServiceUnavailableException;
import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyMember;
import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.model.PolicyType;
import com.trucare.policy.model.Relationship;
import com.trucare.policy.model.TreatmentType;
import com.trucare.policy.repository.PolicyCoverageRepository;
import com.trucare.policy.repository.PolicyMemberRepository;
import com.trucare.policy.repository.PolicyRepository;
import feign.FeignException;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PolicyManagementServiceTest {

    private PolicyRepository policyRepository;
    private PolicyMemberRepository policyMemberRepository;
    private PolicyCoverageRepository policyCoverageRepository;
    private MemberClient memberServiceClient;
    private PolicyManagementService service;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMemberRepository = mock(PolicyMemberRepository.class);
        policyCoverageRepository = mock(PolicyCoverageRepository.class);
        memberServiceClient = mock(MemberClient.class);
        service = new PolicyManagementService(
                policyRepository, policyMemberRepository, policyCoverageRepository,
                memberServiceClient, Mappers.getMapper(PolicyMapper.class));
    }

    @Test
    void createsPolicyAndInitializesAvailableCoverage() {
        when(policyRepository.existsByPolicyNumber("POL1001")).thenReturn(false);
        when(policyRepository.saveAndFlush(any(Policy.class))).thenAnswer(invocation -> {
            Policy policy = invocation.getArgument(0);
            policy.setId(1001L);
            return policy;
        });

        var response = service.createPolicy(policyRequest(
                new BigDecimal("500000.00"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")));

        assertEquals(1001L, response.id());
        assertEquals(new BigDecimal("500000.00"), response.availableCoverageAmount());
        assertEquals(1, response.coverages().size());
    }

    @Test
    void rejectsDuplicatePolicyNumber() {
        when(policyRepository.existsByPolicyNumber("POL1001")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.createPolicy(policyRequest(
                        new BigDecimal("500000"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))));
        verify(policyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidDateRangeAndNonPositiveCoverage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createPolicy(policyRequest(
                        new BigDecimal("500000"), LocalDate.parse("2026-12-31"), LocalDate.parse("2026-01-01"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createPolicy(policyRequest(
                        BigDecimal.ZERO, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))));
        verify(policyRepository, never()).saveAndFlush(any());
    }

    @Test
    void enrollsMemberAfterMemberServiceValidation() {
        Policy policy = policy();
        when(policyRepository.findWithCoveragesById(1001L)).thenReturn(Optional.of(policy));
        when(memberServiceClient.validateMember("M123456")).thenReturn(true);
        when(policyMemberRepository.findByPolicyIdAndMemberId(1001L, "M123456"))
                .thenReturn(Optional.empty());
        when(policyMemberRepository.saveAndFlush(any(PolicyMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var enrollment = service.enrollMember(1001L,
                new EnrollMemberRequest("M123456", Relationship.SELF, LocalDate.parse("2026-01-01")));

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.status());
        assertEquals("M123456", enrollment.memberId());
        verify(memberServiceClient).validateMember("M123456");
    }

    @Test
    void rejectsDuplicateActiveEnrollment() {
        Policy policy = policy();
        PolicyMember existing = new PolicyMember();
        existing.setId(3L);
        existing.setStatus(EnrollmentStatus.ACTIVE);
        when(policyRepository.findWithCoveragesById(1001L)).thenReturn(Optional.of(policy));
        when(memberServiceClient.validateMember("M123456")).thenReturn(true);
        when(policyMemberRepository.findByPolicyIdAndMemberId(1001L, "M123456"))
                .thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> service.enrollMember(1001L,
                new EnrollMemberRequest("M123456", Relationship.SELF, LocalDate.parse("2026-01-01"))));
        verify(policyMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownMemberAndUnavailableMemberService() {
        when(policyRepository.findWithCoveragesById(1001L)).thenReturn(Optional.of(policy()));
        when(memberServiceClient.validateMember("missing")).thenReturn(false);
        assertThrows(MemberNotFoundException.class, () -> service.enrollMember(1001L,
                new EnrollMemberRequest("missing", Relationship.SELF, LocalDate.now())));

        when(memberServiceClient.validateMember("M123456"))
                .thenThrow(mock(FeignException.class));
        assertThrows(MemberServiceUnavailableException.class, () -> service.enrollMember(1001L,
                new EnrollMemberRequest("M123456", Relationship.SELF, LocalDate.now())));
    }

    @Test
    void oneMemberCanHaveMultiplePolicies() {
        Policy first = policy();
        Policy second = policy();
        second.setId(1002L);
        second.setPolicyNumber("POL1002");
        when(policyRepository.findAllActiveForMember("M123456")).thenReturn(List.of(first, second));

        var policies = service.listMemberPolicies("M123456");

        assertEquals(2, policies.size());
    }

    @Test
    void duplicateRaceIsReportedAsConflict() {
        when(policyRepository.existsByPolicyNumber("POL1001")).thenReturn(false);
        when(policyRepository.saveAndFlush(any(Policy.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(ConflictException.class,
                () -> service.createPolicy(policyRequest(
                        new BigDecimal("500000"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))));
    }

    private CreatePolicyRequest policyRequest(BigDecimal amount, LocalDate start, LocalDate end) {
        return new CreatePolicyRequest(
                "POL1001",
                "Family Health",
                PolicyType.FAMILY_FLOATER,
                amount,
                start,
                end,
                PolicyStatus.ACTIVE,
                List.of(new PolicyCoverageRequest(
                        TreatmentType.HOSPITALIZATION, new BigDecimal("300000"), 30, "Hospital care")));
    }

    private Policy policy() {
        Policy policy = new Policy();
        policy.setId(1001L);
        policy.setPolicyNumber("POL1001");
        policy.setPolicyName("Family Health");
        policy.setPolicyType(PolicyType.FAMILY_FLOATER);
        policy.setCoverageAmount(new BigDecimal("500000"));
        policy.setAvailableCoverageAmount(new BigDecimal("500000"));
        policy.setStartDate(LocalDate.parse("2026-01-01"));
        policy.setEndDate(LocalDate.parse("2026-12-31"));
        policy.setStatus(PolicyStatus.ACTIVE);
        return policy;
    }
}
