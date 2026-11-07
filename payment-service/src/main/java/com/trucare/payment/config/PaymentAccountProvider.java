package com.trucare.payment.config;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface PaymentAccountProvider {
    UserDetailsService create(PasswordEncoder passwordEncoder);
}
