package com.trucare.settlement.config;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface SettlementAccountProvider {
    UserDetailsService createUserDetailsService(PasswordEncoder passwordEncoder);
}
