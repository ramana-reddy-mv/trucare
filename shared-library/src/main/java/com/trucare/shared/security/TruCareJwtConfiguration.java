package com.trucare.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class TruCareJwtConfiguration {

    @Bean
    SecretKey truCareJwtSecretKey(@Value("${TRUCARE_JWT_SECRET_BASE64}") String encodedSecret) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("TRUCARE_JWT_SECRET_BASE64 must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("TRUCARE_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtDecoder truCareJwtDecoder(SecretKey truCareJwtSecretKey,
                                 @Value("${TRUCARE_JWT_ISSUER:trucare-auth-service}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(truCareJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            Object roles = jwt.getClaims().get("roles");
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !(roles instanceof Collection<?> values)
                    || values.isEmpty()
                    || values.stream().anyMatch(role -> !(role instanceof String))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required subject or role claims are missing", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), requiredClaims));
        return decoder;
    }

    @Bean
    JwtEncoder truCareJwtEncoder(SecretKey truCareJwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(truCareJwtSecretKey));
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> truCareJwtAuthenticationConverter() {
        return new TruCareJwtAuthenticationConverter();
    }

    @Bean
    TruCareJwtTokenService truCareJwtTokenService(
            JwtEncoder truCareJwtEncoder,
            @Value("${TRUCARE_JWT_ISSUER:trucare-auth-service}") String issuer,
            @Value("${TRUCARE_SERVICE_TOKEN_TTL_SECONDS:300}") long tokenTtlSeconds) {
        return new TruCareJwtTokenService(truCareJwtEncoder, issuer, tokenTtlSeconds);
    }

    private static final class TruCareJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {
        private final JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        private TruCareJwtAuthenticationConverter() {
            authoritiesConverter.setAuthoritiesClaimName("roles");
            authoritiesConverter.setAuthorityPrefix("ROLE_");
        }

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            return new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt),
                    jwt.getSubject());
        }
    }
}
