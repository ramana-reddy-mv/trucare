package com.trucare.notification.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

public interface NotificationSecurityPolicy {
    SecurityFilterChain build(HttpSecurity http) throws Exception;
}
