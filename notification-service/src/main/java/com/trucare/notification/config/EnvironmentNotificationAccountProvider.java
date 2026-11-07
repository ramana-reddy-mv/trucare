package com.trucare.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentNotificationAccountProvider implements NotificationAccountProvider {
    private final String internalUsername;
    private final String internalPassword;
    private final String adminUsername;
    private final String adminPassword;

    public EnvironmentNotificationAccountProvider(
            @Value("${notification.security.internal-username}") String internalUsername,
            @Value("${notification.security.internal-password}") String internalPassword,
            @Value("${notification.security.admin-username}") String adminUsername,
            @Value("${notification.security.admin-password}") String adminPassword) {
        this.internalUsername = internalUsername;
        this.internalPassword = internalPassword;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public UserDetailsService create(PasswordEncoder encoder) {
        if (internalUsername.isBlank() || internalPassword.isBlank()
                || adminUsername.isBlank() || adminPassword.isBlank()
                || internalUsername.equals(adminUsername)) {
            throw new IllegalStateException("Distinct Notification Service internal and admin credentials must be configured");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(internalUsername).password(encoder.encode(internalPassword))
                        .roles("NOTIFICATION_INTERNAL").build(),
                User.withUsername(adminUsername).password(encoder.encode(adminPassword))
                        .roles("NOTIFICATION_ADMIN").build());
    }
}
