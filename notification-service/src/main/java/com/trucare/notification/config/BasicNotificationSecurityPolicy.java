package com.trucare.notification.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Component
public class BasicNotificationSecurityPolicy implements NotificationSecurityPolicy {
    @Override
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/notification-templates/**").hasRole("NOTIFICATION_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/notifications").hasRole("NOTIFICATION_INTERNAL")
                        .requestMatchers(HttpMethod.POST, "/api/notifications/*/retry").hasAnyRole(
                                "NOTIFICATION_INTERNAL", "NOTIFICATION_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/notifications",
                                "/api/notifications/reference/**").hasAnyRole(
                                "NOTIFICATION_INTERNAL", "NOTIFICATION_ADMIN")
                        .requestMatchers("/api/notifications/recipient/**",
                                "/api/notifications/in-app/**",
                                "/api/notifications/*/read",
                                "/api/notifications/*").authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
