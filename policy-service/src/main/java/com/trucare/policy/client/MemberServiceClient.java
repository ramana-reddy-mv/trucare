package com.trucare.policy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service", contextId = "policyMemberValidationClient")
public interface MemberServiceClient {

    @GetMapping("/api/members/validate/{memberId}")
    Boolean validateMember(@PathVariable("memberId") String memberId);
}
