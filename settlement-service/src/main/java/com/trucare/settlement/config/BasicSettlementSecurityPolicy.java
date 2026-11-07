package com.trucare.settlement.config;

import com.trucare.shared.security.TruCareJwtConfiguration;
import com.trucare.shared.security.TruCareFeignSecurityConfiguration;
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
@Import({TruCareJwtConfiguration.class, TruCareFeignSecurityConfiguration.class})
public class BasicSettlementSecurityPolicy implements SettlementSecurityPolicy {
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    public BasicSettlementSecurityPolicy(
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
                        .requestMatchers(HttpMethod.POST, "/api/settlements/*/payment-status")
                        .hasAnyRole("PAYMENT_SERVICE", "SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/settlements/**")
                        .hasAnyRole("ADMIN", "SETTLEMENT_OFFICER", "SETTLEMENT_OPERATOR",
                                "PAYMENT_SERVICE", "SERVICE")
                        .requestMatchers("/api/settlements/**")
                        .hasAnyRole("ADMIN", "SETTLEMENT_OFFICER", "SETTLEMENT_OPERATOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
