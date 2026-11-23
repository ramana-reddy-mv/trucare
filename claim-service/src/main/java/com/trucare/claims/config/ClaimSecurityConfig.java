package com.trucare.claims.config;

import com.trucare.shared.security.TruCareFeignSecurityConfiguration;
import com.trucare.shared.security.TruCareJwtConfiguration;
import com.trucare.shared.security.TruCareServiceAccountConfiguration;
import com.trucare.shared.observability.CorrelationIdConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import({TruCareJwtConfiguration.class, TruCareFeignSecurityConfiguration.class,
        TruCareServiceAccountConfiguration.class, CorrelationIdConfiguration.class})
public class ClaimSecurityConfig {
    @Bean
    SecurityFilterChain claimSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> truCareJwtAuthenticationConverter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/claims/by-claim-id/**").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/claims")
                        .hasAnyRole("PENDING_MEMBER", "CLAIM_OFFICER", "SERVICE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/claims/**")
                        .hasAnyRole("CLAIM_OFFICER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/claims/updateClaim/**")
                        .hasAnyRole("CLAIM_OFFICER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/claims/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(truCareJwtAuthenticationConverter)))
                .build();
    }
}
