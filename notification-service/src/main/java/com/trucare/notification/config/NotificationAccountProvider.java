package com.trucare.notification.config;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface NotificationAccountProvider {
    UserDetailsService create(PasswordEncoder encoder);
}
