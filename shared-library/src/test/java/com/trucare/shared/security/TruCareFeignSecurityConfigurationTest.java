package com.trucare.shared.security;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
