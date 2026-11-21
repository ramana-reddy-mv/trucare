package com.trucare.shared.security;

import feign.RequestInterceptor;
import com.trucare.shared.observability.CorrelationId;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class TruCareFeignSecurityConfiguration {

    @Bean
    RequestInterceptor truCareServiceAuthenticationInterceptor(
            @Value("${TRUCARE_SERVICE_USERNAME}") String username,
            @Value("${TRUCARE_SERVICE_PASSWORD}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("TruCare service credentials must be configured");
        }
        String encodedCredentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return requestTemplate -> requestTemplate.header("Authorization", "Basic " + encodedCredentials);
    }

    @Bean
    RequestInterceptor truCareCorrelationIdInterceptor() {
        return requestTemplate -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (CorrelationId.isValid(correlationId)) {
                requestTemplate.header(CorrelationId.HEADER, correlationId);
            }
        };
    }

}
