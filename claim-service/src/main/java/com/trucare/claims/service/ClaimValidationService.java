package com.trucare.claims.service;

import com.trucare.shared.client.MemberClient;
import com.trucare.shared.client.ProviderClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        logger.warn("Member validation service failed after retries; claim creation cannot continue");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Member validity could not be verified; retry claim submission");
    }

    @Retry(name = "providerValidation")
    @CircuitBreaker(name = "providerValidation", fallbackMethod = "providerValidationFallback")
    public boolean validateProvider(String providerId) {
        return Boolean.TRUE.equals(providerValidationClient.validateProvider(providerId));
    }

    private boolean providerValidationFallback(String providerId, Throwable throwable) {
        logger.warn("Provider validation service failed after retries; claim creation cannot continue");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Provider validity could not be verified; retry claim submission");
    }
}
