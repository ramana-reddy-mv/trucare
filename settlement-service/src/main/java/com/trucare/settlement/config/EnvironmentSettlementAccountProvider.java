package com.trucare.settlement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSettlementAccountProvider implements SettlementAccountProvider {

    private final String operatorUsername;
    private final String operatorPassword;
    private final String paymentUsername;
    private final String paymentPassword;

    public EnvironmentSettlementAccountProvider(
            @Value("${settlement.security.operator-username}") String operatorUsername,
            @Value("${settlement.security.operator-password}") String operatorPassword,
            @Value("${settlement.security.payment-username}") String paymentUsername,
            @Value("${settlement.security.payment-password}") String paymentPassword) {
        this.operatorUsername = operatorUsername;
        this.operatorPassword = operatorPassword;
        this.paymentUsername = paymentUsername;
        this.paymentPassword = paymentPassword;
    }

    @Override
    public UserDetailsService createUserDetailsService(PasswordEncoder passwordEncoder) {
        if (operatorPassword.isBlank() || paymentPassword.isBlank()
                || operatorUsername.isBlank() || paymentUsername.isBlank()) {
            throw new IllegalStateException("Settlement operator and payment credentials must be configured");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(operatorUsername).password(passwordEncoder.encode(operatorPassword))
                        .roles("SETTLEMENT_OPERATOR").build(),
                User.withUsername(paymentUsername).password(passwordEncoder.encode(paymentPassword))
                        .roles("PAYMENT_SERVICE").build());
    }
}
