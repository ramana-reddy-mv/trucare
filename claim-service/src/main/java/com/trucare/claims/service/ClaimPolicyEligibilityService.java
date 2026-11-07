package com.trucare.claims.service;

import com.trucare.claims.client.PolicyClient;
import com.trucare.claims.client.PolicyEligibilityRequest;
import com.trucare.claims.client.PolicyEligibilityResponse;
import com.trucare.shared.claim.CreateClaimDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClaimPolicyEligibilityService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimPolicyEligibilityService.class);

    private final PolicyClient policyClient;

    public ClaimPolicyEligibilityService(PolicyClient policyClient) {
        this.policyClient = policyClient;
    }

    @Retry(name = "policyEligibility")
    @CircuitBreaker(name = "policyEligibility", fallbackMethod = "policyServiceUnavailable")
    public PolicyEligibilityResponse checkEligibility(CreateClaimDTO claim) {
        PolicyEligibilityRequest request = new PolicyEligibilityRequest(
                claim.getMemberId(),
                claim.getPolicyNumber(),
                claim.getTreatmentType(),
                claim.getTreatmentDate(),
                claim.getClaimAmount());
        PolicyEligibilityResponse response = policyClient.checkEligibility(request);
        if (response == null || response.eligible() == null) {
            throw new IllegalStateException("Policy Service returned an invalid eligibility response");
        }
        if (!claim.getPolicyNumber().equals(response.policyNumber())
                || !claim.getMemberId().equals(response.memberId())
                || !claim.getTreatmentType().equals(response.treatmentType())
                || response.reason() == null
                || response.reason().isBlank()) {
            throw new IllegalStateException("Policy Service returned an inconsistent eligibility response");
        }
        if (response.eligible()
                && (response.policyId() == null
                || response.maximumEligibleAmount() == null
                || claim.getClaimAmount() == null
                || claim.getClaimAmount().compareTo(response.maximumEligibleAmount()) > 0)) {
            throw new IllegalStateException("Policy Service returned an inconsistent eligibility response");
        }
        return response;
    }

    PolicyEligibilityResponse policyServiceUnavailable(
            CreateClaimDTO claim, Throwable throwable) {
        logger.error("Policy Service eligibility check failed; claim cannot be submitted", throwable);
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Policy eligibility could not be verified; retry claim submission",
                throwable);
    }
}
