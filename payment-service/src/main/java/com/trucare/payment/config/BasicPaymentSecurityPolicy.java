package com.trucare.payment.config;

import com.trucare.shared.security.TruCareJwtConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Component
@Import(TruCareJwtConfiguration.class)
public class BasicPaymentSecurityPolicy implements PaymentSecurityPolicy {
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    public BasicPaymentSecurityPolicy(
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Override
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/reconcile",
                                "/api/payments/*/callbacks/retry")
                        .hasAnyRole("ADMIN", "PAYMENT_SERVICE", "PAYMENT_INTERNAL")
                        .requestMatchers(HttpMethod.GET, "/api/payments/**")
                        .hasAnyRole("ADMIN", "PAYMENT_OFFICER", "PAYMENT_OPERATOR")
                        .requestMatchers("/api/payments/**")
                        .hasAnyRole("ADMIN", "PAYMENT_OFFICER", "PAYMENT_OPERATOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
