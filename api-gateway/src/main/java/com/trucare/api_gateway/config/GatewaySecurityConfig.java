package com.trucare.api_gateway.config;

import com.trucare.shared.security.TruCareJwtConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@Import(TruCareJwtConfiguration.class)
public class GatewaySecurityConfig {

    @Bean
    ReactiveJwtDecoder truCareReactiveJwtDecoder(JwtDecoder jwtDecoder) {
        return token -> Mono.fromCallable(() -> jwtDecoder.decode(token));
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> truCareJwtAuthenticationConverter) {
        ReactiveJwtAuthenticationConverterAdapter converter =
                new ReactiveJwtAuthenticationConverterAdapter(truCareJwtAuthenticationConverter);
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.POST, "/auth/addUser", "/auth/register",
                                "/auth/token", "/auth/login").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
