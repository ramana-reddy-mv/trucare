package com.trucare.policy.controller;

import com.trucare.policy.dto.CreatePolicyRequest;
import com.trucare.policy.dto.EnrollMemberRequest;
import com.trucare.policy.dto.EligibilityCheckRequest;
import com.trucare.policy.dto.EligibilityCheckResponse;
import com.trucare.policy.dto.PolicyCoverageSummaryResponse;
import com.trucare.policy.dto.PolicyMemberResponse;
import com.trucare.policy.dto.PolicyResponse;
import com.trucare.policy.dto.UpdatePolicyRequest;
import com.trucare.policy.service.PolicyEligibilityService;
import com.trucare.policy.service.PolicyManagementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyManagementService policyManagementService;
    private final PolicyEligibilityService policyEligibilityService;

    public PolicyController(
            PolicyManagementService policyManagementService,
            PolicyEligibilityService policyEligibilityService) {
        this.policyManagementService = policyManagementService;
        this.policyEligibilityService = policyEligibilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        return policyManagementService.createPolicy(request);
    }

    @GetMapping("/{policyId}")
    public PolicyResponse getPolicy(@PathVariable Long policyId) {
        return policyManagementService.getPolicy(policyId);
    }

    @GetMapping
    public Page<PolicyResponse> listPolicies(
            @PageableDefault(size = 20, sort = "policyNumber") Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("Page size cannot exceed 100");
        }
        return policyManagementService.listPolicies(pageable);
    }

    @PutMapping("/{policyId}")
    public PolicyResponse updatePolicy(
            @PathVariable Long policyId,
            @Valid @RequestBody UpdatePolicyRequest request) {
        return policyManagementService.updatePolicy(policyId, request);
    }

    @GetMapping("/number/{policyNumber}")
    public PolicyResponse getPolicyByNumber(@PathVariable String policyNumber) {
        return policyManagementService.getPolicyByNumber(policyNumber);
    }

    @PostMapping("/{policyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyMemberResponse enrollMember(
            @PathVariable Long policyId,
            @Valid @RequestBody EnrollMemberRequest request) {
        return policyManagementService.enrollMember(policyId, request);
    }

    @GetMapping("/{policyId}/members")
    public List<PolicyMemberResponse> listPolicyMembers(@PathVariable Long policyId) {
        return policyManagementService.listPolicyMembers(policyId);
    }

    @GetMapping("/member/{memberId}")
    public List<PolicyResponse> listMemberPolicies(@PathVariable String memberId) {
        return policyManagementService.listMemberPolicies(memberId);
    }

    @DeleteMapping("/{policyId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateEnrollment(@PathVariable Long policyId, @PathVariable String memberId) {
        policyManagementService.deactivateEnrollment(policyId, memberId);
    }

    @PostMapping("/eligibility/check")
    public EligibilityCheckResponse checkEligibility(@Valid @RequestBody EligibilityCheckRequest request) {
        return policyEligibilityService.checkEligibility(request);
    }

    @GetMapping("/{policyId}/coverage")
    public PolicyCoverageSummaryResponse getCoverage(@PathVariable Long policyId) {
        return policyManagementService.getCoverage(policyId);
    }
}
