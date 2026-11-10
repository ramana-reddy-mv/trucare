package com.trucare.shared.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service", contextId = "sharedMemberClient")
public interface MemberClient {

    @GetMapping("/api/members/validate/{memberId}")
    Boolean validateMember(@PathVariable("memberId") String memberId);
}
