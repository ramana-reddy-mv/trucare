package com.trucare.claims.client;

import org.springframework.stereotype.Component;

@Component
public class ProviderValidationClientFallback implements ProviderValidationClient {

    @Override
    public Boolean validateProvider(String providerId) {
        return false;
    }
}
