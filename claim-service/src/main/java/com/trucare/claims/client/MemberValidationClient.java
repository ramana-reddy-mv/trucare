package com.trucare.claims.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service", contextId = "memberValidationClient", url = "http://localhost:8080/api/members")
public interface MemberValidationClient {

    @GetMapping("/validate/{memberId}")
    Boolean validateMember(@PathVariable("memberId") String memberId);
}
