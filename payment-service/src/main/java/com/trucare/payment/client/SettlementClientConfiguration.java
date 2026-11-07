package com.trucare.payment.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SettlementClientConfiguration {
    @Bean
    RequestInterceptor settlementAuthentication(
            @Value("${settlement.client.username}") String username,
            @Value("${settlement.client.password}") String password) {
        return template -> {
            if (username.isBlank() || password.isBlank()) {
                throw new IllegalStateException("Settlement payment-service credentials must be configured");
            }
            String credentials = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + credentials);
        };
    }
}
