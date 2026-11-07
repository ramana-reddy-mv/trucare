package com.trucare.payment.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

public interface PaymentSecurityPolicy {
    SecurityFilterChain build(HttpSecurity http) throws Exception;
}
