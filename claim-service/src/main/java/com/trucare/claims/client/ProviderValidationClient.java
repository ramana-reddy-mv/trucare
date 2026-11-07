package com.trucare.claims.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "provider-service", contextId = "providerValidationClient", url = "http://localhost:8080/api/providers")
public interface ProviderValidationClient {

    @GetMapping("/validate/{providerId}")
    Boolean validateProvider(@PathVariable("providerId") String providerId);
}
