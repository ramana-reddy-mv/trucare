package com.trucare.policy.service;

import com.trucare.shared.client.MemberClient;
import com.trucare.policy.dto.CreatePolicyRequest;
import com.trucare.policy.dto.EnrollMemberRequest;
import com.trucare.policy.dto.PolicyMemberResponse;
import com.trucare.policy.dto.PolicyResponse;
import com.trucare.policy.dto.UpdatePolicyRequest;
import com.trucare.policy.exception.ConflictException;
import com.trucare.policy.exception.EnrollmentNotFoundException;
import com.trucare.policy.exception.MemberNotFoundException;
import com.trucare.policy.exception.MemberServiceUnavailableException;
import com.trucare.policy.exception.PolicyNotFoundException;
import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyMember;
import com.trucare.policy.model.PolicyCoverage;
import com.trucare.policy.dto.PolicyCoverageSummaryResponse;
import com.trucare.policy.repository.PolicyMemberRepository;
import com.trucare.policy.repository.PolicyCoverageRepository;
import com.trucare.policy.repository.PolicyRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PolicyManagementService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyManagementService.class);

    private final PolicyRepository policyRepository;
    private final PolicyMemberRepository policyMemberRepository;
    private final PolicyCoverageRepository policyCoverageRepository;
    private final MemberClient memberServiceClient;
    private final PolicyMapper policyMapper;

    public PolicyManagementService(
            PolicyRepository policyRepository,
            PolicyMemberRepository policyMemberRepository,
            PolicyCoverageRepository policyCoverageRepository,
            MemberClient memberServiceClient,
            PolicyMapper policyMapper) {
        this.policyRepository = policyRepository;
        this.policyMemberRepository = policyMemberRepository;
        this.policyCoverageRepository = policyCoverageRepository;
        this.memberServiceClient = memberServiceClient;
        this.policyMapper = policyMapper;
    }

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        validatePolicyValues(request.startDate(), request.endDate(), request.coverageAmount());
        if (policyRepository.existsByPolicyNumber(request.policyNumber().trim())) {
            throw new ConflictException("Policy number already exists");
        }

        Policy policy = new Policy();
        policy.setPolicyNumber(request.policyNumber().trim());
        policy.setPolicyName(request.policyName().trim());
        policy.setPolicyType(request.policyType());
        policy.setCoverageAmount(request.coverageAmount());
        policy.setAvailableCoverageAmount(request.coverageAmount());
        policy.setStartDate(request.startDate());
        policy.setEndDate(request.endDate());
        policy.setStatus(request.status());
        policyMapper.replaceCoverages(policy, request.coverages());

        try {
            return policyMapper.toResponse(policyRepository.saveAndFlush(policy));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Policy number or treatment coverage already exists");
        }
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(Long policyId) {
        return policyMapper.toResponse(findPolicy(policyId));
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicyByNumber(String policyNumber) {
        Policy policy = policyRepository.findWithCoveragesByPolicyNumber(policyNumber)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyNumber));
        return policyMapper.toResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<PolicyResponse> listPolicies(Pageable pageable) {
        Page<Policy> policies = policyRepository.findAll(pageable);
        List<Long> policyIds = policies.getContent().stream().map(Policy::getId).toList();
        if (policyIds.isEmpty()) {
            return policies.map(policy -> policyMapper.toResponse(policy));
        }
        var coveragesByPolicyId = new java.util.HashMap<Long, List<PolicyCoverage>>();
        policyCoverageRepository.findAllByPolicyIdIn(policyIds).forEach(coverage ->
                coveragesByPolicyId.computeIfAbsent(
                        coverage.getPolicy().getId(), ignored -> new java.util.ArrayList<>()).add(coverage));
        return policies.map(policy -> {
            policy.setCoverages(coveragesByPolicyId.getOrDefault(policy.getId(), List.of()));
            return policyMapper.toResponse(policy);
        });
    }

    @Transactional(readOnly = true)
    public PolicyCoverageSummaryResponse getCoverage(Long policyId) {
        Policy policy = findPolicy(policyId);
        return new PolicyCoverageSummaryResponse(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getCoverageAmount(),
                policy.getAvailableCoverageAmount(),
                policy.getCoverages().stream().map(policyMapper::toResponse).toList());
    }

    @Transactional
    public PolicyResponse updatePolicy(Long policyId, UpdatePolicyRequest request) {
        validatePolicyValues(request.startDate(), request.endDate(), request.coverageAmount());
        Policy policy = findPolicy(policyId);

        if (!policy.getPolicyNumber().equals(request.policyNumber().trim())
                && policyRepository.existsByPolicyNumber(request.policyNumber().trim())) {
            throw new ConflictException("Policy number already exists");
        }

        BigDecimal usedCoverage = policy.getCoverageAmount().subtract(policy.getAvailableCoverageAmount());
        if (request.coverageAmount().compareTo(usedCoverage) < 0) {
            throw new ConflictException("Coverage amount cannot be lower than already utilized coverage");
        }

        policy.setPolicyNumber(request.policyNumber().trim());
        policy.setPolicyName(request.policyName().trim());
        policy.setPolicyType(request.policyType());
        policy.setAvailableCoverageAmount(request.coverageAmount().subtract(usedCoverage));
        policy.setCoverageAmount(request.coverageAmount());
        policy.setStartDate(request.startDate());
        policy.setEndDate(request.endDate());
        policy.setStatus(request.status());
        policyMapper.replaceCoverages(policy, request.coverages());

        try {
            return policyMapper.toResponse(policyRepository.saveAndFlush(policy));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Policy number or treatment coverage already exists");
        }
    }

    @Transactional
    public PolicyMemberResponse enrollMember(Long policyId, EnrollMemberRequest request) {
        Policy policy = findPolicy(policyId);
        validateMemberExists(request.memberId());
        PolicyMember enrollment = policyMemberRepository.findByPolicyIdAndMemberId(policyId, request.memberId())
                .orElseGet(PolicyMember::new);

        if (enrollment.getId() != null && enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            throw new ConflictException("Member is already actively enrolled in this policy");
        }

        enrollment.setPolicy(policy);
        enrollment.setMemberId(request.memberId());
        enrollment.setRelationship(request.relationship());
        enrollment.setEnrollmentDate(request.enrollmentDate());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        try {
            return policyMapper.toResponse(policyMemberRepository.saveAndFlush(enrollment));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Member is already enrolled in this policy");
        }
    }

    @Transactional(readOnly = true)
    public List<PolicyMemberResponse> listPolicyMembers(Long policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new PolicyNotFoundException("Policy not found: " + policyId);
        }
        return policyMemberRepository.findAllByPolicyIdOrderByMemberId(policyId).stream()
                .map(policyMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> listMemberPolicies(String memberId) {
        return policyRepository.findAllActiveForMember(memberId).stream()
                .map(policyMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateEnrollment(Long policyId, String memberId) {
        PolicyMember enrollment = policyMemberRepository.findByPolicyIdAndMemberId(policyId, memberId)
                .orElseThrow(() -> new EnrollmentNotFoundException(
                        "Member " + memberId + " is not enrolled in policy " + policyId));
        enrollment.setStatus(EnrollmentStatus.INACTIVE);
        policyMemberRepository.save(enrollment);
    }

    private Policy findPolicy(Long policyId) {
        return policyRepository.findWithCoveragesById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));
    }

    private void validateMemberExists(String memberId) {
        final Boolean memberExists;
        try {
            memberExists = memberServiceClient.validateMember(memberId);
        } catch (FeignException exception) {
            logger.warn("Member Service failed while validating member {}", memberId, exception);
            throw new MemberServiceUnavailableException("Member Service is unavailable", exception);
        }
        if (memberExists == null) {
            throw new MemberServiceUnavailableException("Member Service returned an invalid validation response");
        }
        if (!memberExists) {
            throw new MemberNotFoundException("Member not found: " + memberId);
        }
    }

    private void validatePolicyValues(
            java.time.LocalDate startDate, java.time.LocalDate endDate, BigDecimal coverageAmount) {
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Policy endDate must be after startDate");
        }
        if (coverageAmount == null || coverageAmount.signum() <= 0) {
            throw new IllegalArgumentException("Policy coverageAmount must be greater than zero");
        }
    }
}
