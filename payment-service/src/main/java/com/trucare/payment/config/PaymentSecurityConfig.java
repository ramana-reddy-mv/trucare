package com.trucare.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PaymentSecurityConfig {
    @Bean
    PasswordEncoder paymentPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService paymentUsers(PaymentAccountProvider provider, PasswordEncoder paymentPasswordEncoder) {
        return provider.create(paymentPasswordEncoder);
    }

    @Bean
    SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http, PaymentSecurityPolicy policy)
            throws Exception {
        return policy.build(http);
    }
}
