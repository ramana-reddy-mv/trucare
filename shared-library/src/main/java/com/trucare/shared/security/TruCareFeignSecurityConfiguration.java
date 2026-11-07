package com.trucare.shared.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TruCareFeignSecurityConfiguration {

    @Bean
    RequestInterceptor truCareServiceTokenInterceptor(
            TruCareJwtTokenService tokenService,
            @Value("${spring.application.name}") String serviceName) {
        return requestTemplate ->
                requestTemplate.header("Authorization", "Bearer " + tokenService.createServiceToken(serviceName));
    }
}
