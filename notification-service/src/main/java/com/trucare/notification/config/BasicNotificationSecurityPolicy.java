package com.trucare.notification.config;

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
public class BasicNotificationSecurityPolicy implements NotificationSecurityPolicy {
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    public BasicNotificationSecurityPolicy(
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
                        .requestMatchers("/api/notification-templates/**")
                        .hasAnyRole("ADMIN", "NOTIFICATION_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/notifications")
                        .hasAnyRole("SERVICE", "NOTIFICATION_INTERNAL")
                        .requestMatchers(HttpMethod.POST, "/api/notifications/*/retry").hasAnyRole(
                                "SERVICE", "ADMIN", "NOTIFICATION_INTERNAL", "NOTIFICATION_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/notifications",
                                "/api/notifications/reference/**").hasAnyRole("SERVICE", "ADMIN",
                                "NOTIFICATION_INTERNAL", "NOTIFICATION_ADMIN")
                        .requestMatchers("/api/notifications/recipient/**",
                                "/api/notifications/in-app/**",
                                "/api/notifications/*/read",
                                "/api/notifications/*").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
