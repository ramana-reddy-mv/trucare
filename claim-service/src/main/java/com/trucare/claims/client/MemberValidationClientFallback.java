package com.trucare.claims.client;

import org.springframework.stereotype.Component;

@Component
public class MemberValidationClientFallback implements MemberValidationClient {

    @Override
    public Boolean validateMember(String memberId) {
        return false;
    }
}
