package com.trucare.claims.service;

import com.trucare.shared.client.MemberClient;
import com.trucare.shared.client.ProviderClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClaimValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimValidationService.class);

    private final MemberClient memberValidationClient;
    private final ProviderClient providerValidationClient;

    public ClaimValidationService(MemberClient memberValidationClient,
                                  ProviderClient providerValidationClient) {
        this.memberValidationClient = memberValidationClient;
        this.providerValidationClient = providerValidationClient;
    }

    @Retry(name = "memberValidation")
    @CircuitBreaker(name = "memberValidation", fallbackMethod = "memberValidationFallback")
    public boolean validateMember(String memberId) {
        return Boolean.TRUE.equals(memberValidationClient.validateMember(memberId));
    }

    private boolean memberValidationFallback(String memberId, Throwable throwable) {
        logger.warn("Member validation failed after retries; rejecting claim creation", throwable);
        return false;
    }

    @Retry(name = "providerValidation")
    @CircuitBreaker(name = "providerValidation", fallbackMethod = "providerValidationFallback")
    public boolean validateProvider(String providerId) {
        return Boolean.TRUE.equals(providerValidationClient.validateProvider(providerId));
    }

    private boolean providerValidationFallback(String providerId, Throwable throwable) {
        logger.warn("Provider validation failed after retries; rejecting claim creation", throwable);
        return false;
    }
}
