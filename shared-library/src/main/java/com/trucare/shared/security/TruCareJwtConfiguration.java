package com.trucare.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

@Configuration
public class TruCareJwtConfiguration {

    @Bean
    RSAPublicKey truCareJwtPublicKey(
            @Value("${security.jwt.public-key-base64:${TRUCARE_JWT_PUBLIC_KEY_BASE64}}") String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA public key must be at least 2048 bits");
            }
            return publicKey;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("TRUCARE_JWT_PUBLIC_KEY_BASE64 must contain a valid RSA public key",
                    exception);
        }
    }

    @Bean
    JwtDecoder truCareJwtDecoder(
            RSAPublicKey truCareJwtPublicKey,
            @Value("${security.jwt.issuer:${TRUCARE_JWT_ISSUER:trucare-auth-service}}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(truCareJwtPublicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            Object roles = jwt.getClaims().get("roles");
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !jwt.getAudience().contains("trucare-api")
                    || !(roles instanceof Collection<?> values)
                    || values.isEmpty()
                    || values.stream().anyMatch(role -> !(role instanceof String))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required subject, audience, or role claims are missing",
                                null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), requiredClaims));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> truCareJwtAuthenticationConverter() {
        return new TruCareJwtAuthenticationConverter();
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
            return new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt), jwt.getSubject());
        }
    }
}
