package com.trucare.shared.security;

import feign.RequestTemplate;
import com.trucare.shared.observability.CorrelationId;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TruCareFeignSecurityConfigurationTest {

    @Test
    void addsConfiguredServiceCredentialsAsBasicAuthentication() {
        RequestTemplate template = new RequestTemplate();
        new TruCareFeignSecurityConfiguration()
                .truCareServiceAuthenticationInterceptor("integration-service", "test-password")
                .apply(template);

        String encodedCredentials = Base64.getEncoder().encodeToString(
                "integration-service:test-password".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + encodedCredentials,
                template.headers().get("Authorization").iterator().next());
    }

    @Test
    void propagatesOnlyValidatedCorrelationIds() {
        RequestTemplate template = new RequestTemplate();
        MDC.put(CorrelationId.MDC_KEY, "business-request-42");
        try {
            new TruCareFeignSecurityConfiguration().truCareCorrelationIdInterceptor().apply(template);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }

        assertEquals("business-request-42",
                template.headers().get(CorrelationId.HEADER).iterator().next());

        RequestTemplate invalidTemplate = new RequestTemplate();
        MDC.put(CorrelationId.MDC_KEY, "invalid value");
        try {
            new TruCareFeignSecurityConfiguration().truCareCorrelationIdInterceptor().apply(invalidTemplate);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
        assertFalse(invalidTemplate.headers().containsKey(CorrelationId.HEADER));
    }
}
