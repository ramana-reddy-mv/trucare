package com.trucare.settlement.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

public interface SettlementSecurityPolicy {
    SecurityFilterChain build(HttpSecurity http) throws Exception;
}
