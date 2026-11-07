package com.trucare.payment.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Component
public class BasicPaymentSecurityPolicy implements PaymentSecurityPolicy {
    @Override
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/reconcile",
                                "/api/payments/*/callbacks/retry").hasRole("PAYMENT_INTERNAL")
                        .requestMatchers(HttpMethod.GET, "/api/payments/**").hasRole("PAYMENT_OPERATOR")
                        .requestMatchers("/api/payments/**").hasRole("PAYMENT_OPERATOR")
                        .anyRequest().denyAll())
                .build();
    }
}
