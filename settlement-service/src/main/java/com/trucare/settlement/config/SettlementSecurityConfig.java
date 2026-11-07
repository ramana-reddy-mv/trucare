package com.trucare.settlement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SettlementSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(
            SettlementAccountProvider accountProvider,
            PasswordEncoder passwordEncoder) {
        return accountProvider.createUserDetailsService(passwordEncoder);
    }

    @Bean
    SecurityFilterChain settlementSecurityFilterChain(HttpSecurity http,
                                                       SettlementSecurityPolicy securityPolicy)
            throws Exception {
        return securityPolicy.build(http);
    }
}
