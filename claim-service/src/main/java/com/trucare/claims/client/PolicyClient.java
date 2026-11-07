package com.trucare.claims.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "policy-service", contextId = "claimPolicyEligibilityClient")
public interface PolicyClient {

    @PostMapping("/api/policies/eligibility/check")
    PolicyEligibilityResponse checkEligibility(@RequestBody PolicyEligibilityRequest request);
}
