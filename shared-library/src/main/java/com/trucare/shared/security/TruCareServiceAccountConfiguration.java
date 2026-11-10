package com.trucare.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class TruCareServiceAccountConfiguration {

    @Bean
    PasswordEncoder truCareServicePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService truCareServiceAccount(
            @Value("${TRUCARE_SERVICE_USERNAME}") String username,
            @Value("${TRUCARE_SERVICE_PASSWORD}") String password,
            PasswordEncoder truCareServicePasswordEncoder) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("TruCare service credentials must be configured");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(truCareServicePasswordEncoder.encode(password))
                .roles("SERVICE")
                .build());
    }
}
