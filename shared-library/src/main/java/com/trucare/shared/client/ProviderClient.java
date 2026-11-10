package com.trucare.shared.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "provider-service", contextId = "sharedProviderClient")
public interface ProviderClient {

    @GetMapping("/api/providers/validate/{providerId}")
    Boolean validateProvider(@PathVariable("providerId") String providerId);
}