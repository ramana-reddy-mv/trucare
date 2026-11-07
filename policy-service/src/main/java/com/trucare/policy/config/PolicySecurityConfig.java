package com.trucare.policy.config;

import com.trucare.shared.security.TruCareFeignSecurityConfiguration;
import com.trucare.shared.security.TruCareJwtConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import({TruCareJwtConfiguration.class, TruCareFeignSecurityConfiguration.class})
public class PolicySecurityConfig {
    @Bean
    SecurityFilterChain policySecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> truCareJwtAuthenticationConverter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/policies/eligibility/check").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/policies/**")
                        .hasAnyRole("ADMIN", "CLAIM_OFFICER", "SERVICE")
                        .requestMatchers("/api/policies/**").hasAnyRole("ADMIN", "SERVICE")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(truCareJwtAuthenticationConverter)))
                .build();
    }
}
