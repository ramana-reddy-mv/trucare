package com.trucare.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentPaymentAccountProvider implements PaymentAccountProvider {
    private final String operatorUsername;
    private final String operatorPassword;
    private final String internalUsername;
    private final String internalPassword;

    public EnvironmentPaymentAccountProvider(
            @Value("${payment.security.operator-username}") String operatorUsername,
            @Value("${payment.security.operator-password}") String operatorPassword,
            @Value("${payment.security.internal-username}") String internalUsername,
            @Value("${payment.security.internal-password}") String internalPassword) {
        this.operatorUsername = operatorUsername;
        this.operatorPassword = operatorPassword;
        this.internalUsername = internalUsername;
        this.internalPassword = internalPassword;
    }

    @Override
    public UserDetailsService create(PasswordEncoder encoder) {
        if (operatorUsername.isBlank() || operatorPassword.isBlank()
                || internalUsername.isBlank() || internalPassword.isBlank()) {
            throw new IllegalStateException("Payment operator and internal service credentials must be configured");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(operatorUsername).password(encoder.encode(operatorPassword))
                        .roles("PAYMENT_OPERATOR").build(),
                User.withUsername(internalUsername).password(encoder.encode(internalPassword))
                        .roles("PAYMENT_INTERNAL").build());
    }
}
