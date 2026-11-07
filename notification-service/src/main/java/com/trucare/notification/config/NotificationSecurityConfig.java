package com.trucare.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class NotificationSecurityConfig {
    @Bean
    PasswordEncoder notificationPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService notificationUsers(NotificationAccountProvider provider,
                                         PasswordEncoder notificationPasswordEncoder) {
        return provider.create(notificationPasswordEncoder);
    }

    @Bean
    SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http, NotificationSecurityPolicy policy)
            throws Exception {
        return policy.build(http);
    }
}
