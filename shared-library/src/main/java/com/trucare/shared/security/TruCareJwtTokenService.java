package com.trucare.shared.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TruCareJwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long tokenTtlSeconds;

    TruCareJwtTokenService(JwtEncoder encoder, String issuer, long tokenTtlSeconds) {
        if (tokenTtlSeconds < 1) {
            throw new IllegalStateException("TRUCARE_SERVICE_TOKEN_TTL_SECONDS must be positive");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public String createServiceToken(String serviceName) {
        List<String> roles = new ArrayList<>(List.of("SERVICE"));
        if ("payment-service".equals(serviceName)) {
            roles.add("PAYMENT_SERVICE");
        }
        return encode("service:" + serviceName, roles);
    }

    private String encode(String subject, List<String> roles) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofSeconds(tokenTtlSeconds)))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
